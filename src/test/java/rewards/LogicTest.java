package rewards;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Random;

/** Dependency-free checks for the translated game and the reward economy. */
public final class LogicTest {
    private static final class TestClock extends Clock {
        private Instant instant = Instant.parse("2026-09-28T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        void nextDay() { instant = instant.plusSeconds(86_400); }
        void skip(int days) { instant = instant.plusSeconds(days * 86_400L); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Poker.Card card(String suit, int rank) { return new Poker.Card(suit, rank); }

    private static void economy() throws Exception {
        Path directory = Files.createTempDirectory("rewards-test-");
        Path file = directory.resolve("progress.properties");
        TestClock clock = new TestClock();
        ProgressStore store = new ProgressStore(file, clock);
        store.setGambleDeadlineSupplier(() -> LocalDate.now(clock));
        check(store.points() == 500, "starting balance");

        // daily login bonus, claimed via the first run
        check(store.claimDailyBonus() == 100, "first-day bonus is 100");
        check(store.streakDays() == 1 && store.bonusClaimedToday(), "streak starts at day 1");
        check(store.claimDailyBonus() == 0, "bonus cannot be double-claimed");
        check(store.dailyBonusForToday() == 125, "next streak day previews 125");

        check(store.startPlay() == ProgressStore.StartResult.STARTED, "first paid play");
        check(store.points() == 100, "play deducted 500");
        check(store.startPlay() == ProgressStore.StartResult.NOT_ENOUGH_POINTS, "insufficient balance");

        // run earns: capped base + one-time achievements (first play, 10, 25 pipes)
        ProgressStore.EarnResult earn = store.finishPlay(2_500);
        check(earn.baseAward() == 2_000, "daily per-pipe earnings capped at 2000");
        check(earn.achievements() == 550, "first_play + pipes_10 + pipes_25 = 550");
        check(store.points() == 2_650 && store.earnedToday() == 2_550, "base + achievements awarded");
        check(store.hasUnlocked("first_play") && store.hasUnlocked("pipes_10")
                && store.hasUnlocked("pipes_25") && store.bestRun() == 2_500
                && store.gamesPlayed() == 1, "run stats recorded");
        check(store.startPlay() == ProgressStore.StartResult.DAILY_CAP_REACHED, "no paid play at cap");

        // redemption + trade-in
        check(store.redeem(2), "salad redemption");
        check(store.points() == 1_650, "redemption cost");
        check(store.tradeIn(2) == 500, "trade-in refunds half the cost");
        check(store.points() == 2_150 && store.redeemedCount(2) == 0
                && store.totalRedemptions() == 0, "trade-in removes the item");
        check(store.tradeIn(2) == 0, "trade-in needs an item");

        // double-or-nothing: one gamble per day, win doubles the run's points
        check(store.gambleOpen(), "gamble open after a qualifying run");
        ProgressStore.EarnResult won = store.recordGamble(true, earn);
        check(won.doubleBonus() == 2_550, "win doubles base + achievements");
        check(store.points() == 2_150 + 2_550, "doubled points credited");
        check(!store.gambleOpen(), "gamble used for today");
        boolean threw = false;
        try { store.recordGamble(false, earn); }
        catch (IllegalStateException expected) { threw = true; }
        check(threw, "second gamble on the same day is rejected");

        // persistence
        ProgressStore reloaded = new ProgressStore(file, clock);
        check(reloaded.points() == 4_700 && reloaded.streakDays() == 1
                && reloaded.unlockedDefinitions().size() == 3 && reloaded.pokerWins() == 0
                && reloaded.gamesPlayed() == 1, "balance, streak, achievements persist");

        // poker win achievement
        clock.nextDay();
        reloaded.setGambleDeadlineSupplier(() -> LocalDate.now(clock));
        reloaded.rollDay();
        check(reloaded.earnedToday() == 0, "cap resets on next local day");
        check(reloaded.startPlay() == ProgressStore.StartResult.STARTED, "play allowed next day");
        ProgressStore.EarnResult day2 = reloaded.finishPlay(1);
        check(day2.dailyBonus() == 125 && day2.baseAward() == 1,
                "streak day 2 pays 125 plus the single-pipe award");
        check(reloaded.points() == 4_200 + 126, "day-2 balance");
        check(reloaded.recordPokerWin().get(0).grant() == 200, "first poker win grants 200");
        check(reloaded.pokerWins() == 1 && reloaded.points() == 4_326 + 200
                && reloaded.hasUnlocked("poker_win"), "poker win recorded and credited");

        // streak resets when a day is missed
        TestClock skipped = new TestClock();
        skipped.skip(5);
        ProgressStore late = new ProgressStore(file, skipped);
        check(late.claimDailyBonus() == 100, "missed days reset the streak to base bonus");
        check(late.streakDays() == 1, "streak restarts at one");

        // legacy v1 file loads with defaults
        Path legacy = directory.resolve("legacy.properties");
        Properties v1 = new Properties();
        v1.setProperty("points", "777");
        v1.setProperty("earnedToday", "0");
        v1.setProperty("earnedDate", LocalDate.now(clock).toString());
        v1.store(Files.newOutputStream(legacy), null);
        ProgressStore fromLegacy = new ProgressStore(legacy, clock);
        check(fromLegacy.points() == 777 && fromLegacy.streakDays() == 0
                && fromLegacy.unlockedDefinitions().isEmpty(), "v1 file loads with empty v2 state");
        Files.delete(file);
        Files.delete(legacy);
        Files.delete(directory);
    }

    private static void foodie() throws Exception {
        Path directory = Files.createTempDirectory("rewards-foodie-");
        Path file = directory.resolve("progress.properties");
        Properties seed = new Properties();
        seed.setProperty("points", "5000");
        seed.setProperty("version", "2");
        seed.store(Files.newOutputStream(file), null);
        ProgressStore store = new ProgressStore(file, Clock.systemUTC());
        for (int i = 0; i < 4; i++) {
            check(store.redeem(0), "soda redemption " + (i + 1));
            check(!store.hasUnlocked("redemptions_5"), "no Foodie before five redemptions");
        }
        check(store.redeem(0), "fifth redemption");
        check(store.hasUnlocked("redemptions_5"), "Foodie unlocks on the fifth redemption");
        check(store.points() == 5_000 - 1_500 + 200, "grant credited with the redemption");
        Files.delete(file);
        Files.delete(directory);
    }

    private static void poker() {
        Poker.HandRank fullHouse = Poker.evaluateFive(List.of(card("Hearts", 8), card("Clubs", 8),
                card("Spades", 8), card("Hearts", 3), card("Diamonds", 3)));
        Poker.HandRank flush = Poker.evaluateFive(List.of(card("Hearts", 2), card("Hearts", 5),
                card("Hearts", 8), card("Hearts", 11), card("Hearts", 13)));
        check(fullHouse.compareTo(flush) > 0, "full house beats flush");
        Poker.HandRank wheel = Poker.evaluateFive(List.of(card("Clubs", 14), card("Spades", 2),
                card("Diamonds", 3), card("Hearts", 4), card("Clubs", 5)));
        check(wheel.category() == 4 && wheel.tieBreak().equals(List.of(5)), "ace-low straight");
        Poker.HandRank lowKicker = Poker.evaluateFive(List.of(card("Clubs", 9), card("Hearts", 9),
                card("Spades", 14), card("Clubs", 11), card("Hearts", 7)));
        Poker.HandRank highKicker = Poker.evaluateFive(List.of(card("Clubs", 9), card("Hearts", 9),
                card("Spades", 14), card("Clubs", 11), card("Hearts", 8)));
        check(highKicker.compareTo(lowKicker) > 0, "last pair kicker");
        List<Poker.Card> eight = List.of(card("Clubs", 2), card("Hearts", 4),
                card("Spades", 6), card("Diamonds", 8), card("Clubs", 10),
                card("Clubs", 11), card("Clubs", 12), card("Clubs", 13));
        check(Poker.bestFive(eight).rank().category() == 5, "best five of eight");
    }

    private static void flight() throws Exception {
        SpriteAtlas atlas = new SpriteAtlas();
        PipeFlightGame game = new PipeFlightGame(new Random(1), atlas);
        check(game.stage() == PipeFlightGame.AIR && game.pipeX > 1280,
                "new run starts in noir with a pipe on the right");
        double oldX = game.pipeX;
        game.update(.02, new PipeFlightGame.Input(false, false, false, false, false));
        check(game.pipeX < oldX, "pipe scrolls left");
        for (int stage = 0; stage < 3; stage++) {
            game.failureCount = stage;
            game.playerY = 128;
            game.playerX = 180;
            game.animationFrame = 0;
            check(!game.touchesObstacle(new PipeFlightGame.Rect(180, 128, 2, 2)),
                    "transparent sprite corner must not collide in stage " + stage);
        }
        game.failureCount = 0;
        game.playerY = 128;
        game.pipeX = game.playerX;
        game.gapY = 128 + 128 + 10;
        game.step(0, 0, false);
        check(game.stage() == PipeFlightGame.OCEAN && game.health == 100,
                "card contact advances stage and restores health");
        game.pipeX = game.playerX - PipeFlightGame.PIPE_WIDTH - 1;
        game.step(0, 0, false);
        game.step(0, 0, false);
        check(game.score == 1 && game.totalPipes() == 1, "pipe scores exactly once");
        game.pokerPoints = 4;
        game.upgrades[1] = 2;
        PipeFlightGame restarted = game.restartCampaign();
        check(restarted.stage() == PipeFlightGame.AIR && restarted.totalPipes() == 0,
                "paid retry starts a fresh campaign");
        check(restarted.pokerPoints == 4 && restarted.upgrades[1] == 2,
                "poker progress survives a retry in the app session");
        check(!restarted.canDouble(), "gamble state resets for a new campaign");
    }

    private static void gamble() throws Exception {
        SpriteAtlas atlas = new SpriteAtlas();
        PipeFlightGame game = new PipeFlightGame(new Random(1), atlas);
        check(!game.canDouble(), "gamble is not offered until armed");
        game.offerGamble();
        check(game.canDouble(), "gamble armed for the finished run");
        game.resolveGamble(true);
        check(!game.canDouble() && game.doubleWon(), "flip resolves and wins once");
        game.offerGamble();
        game.resolveGamble(false);
        check(!game.canDouble() && game.doubleWon(), "only one coin flip per run");
    }

    private static void pokerPause() throws Exception {
        PipeFlightGame game = new PipeFlightGame(new Random(12), new SpriteAtlas());
        game.collected.clear();
        for (int i = 0; i < 8; i++) game.collected.add(game.deck.drawTop());
        game.beginPoker();
        check(game.poker != null, "eight pickups start poker");
        check(game.poker.phase == Poker.Phase.DISCARD, "round opens in discard phase");
        double x = game.pipeX;
        game.update(1, new PipeFlightGame.Input(false, false, false, false, false));
        check(game.pipeX == x, "poker pauses flight");
        game.setPokerHold(0, false);
        check(!game.poker.hold.get(0), "hold toggled off");
        Poker.Card before = game.poker.playerCards.get(0);
        game.pokerAction();
        check(game.poker.phase == Poker.Phase.REVEAL && !game.poker.revealed,
                "first action commits the draw");
        check(!game.poker.playerCards.get(0).equals(before), "discarded card replaced");
        game.pokerAction();
        check(game.poker.revealed, "second action reveals showdown");
        game.pokerAction();
        check(game.poker == null, "third action resumes or advances");
    }

    private static void discardDraw() {
        Poker.Deck deck = new Poker.Deck(new Random(7), List.of());
        List<Poker.Card> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++) eight.add(deck.drawTop());
        Poker.Round round = new Poker.Round(eight, deck);
        check(round.phase == Poker.Phase.DISCARD, "round starts in discard phase");
        check(round.hold.size() == 8 && round.hold.stream().allMatch(h -> h),
                "all eight cards start held");
        Poker.Card before = round.playerCards.get(2);
        round.setHold(2, false);
        round.commitDraw(deck);
        check(round.phase == Poker.Phase.REVEAL, "commit locks the hand");
        check(round.playerCards.size() == 8, "hand stays eight cards");
        check(!round.playerCards.get(2).equals(before), "discarded card replaced");
        check(round.playerCards.get(0).equals(eight.get(0)), "kept card unchanged");
        check(deck.size() == 52 - 8 - 8 - 1, "one draw consumed from the deck");
        check(!deck.contains(round.playerCards.get(2)), "drawn card left the deck");
        check(round.playerBest.rank().equals(
                Poker.bestFive(round.playerCards).rank()), "best five recomputed after draw");
        try {
            round.setHold(0, false);
            check(false, "hold after commit should fail");
        } catch (IllegalStateException expected) {
            check(true, "cards locked after commit");
        }
        try {
            round.commitDraw(deck);
            check(false, "second commit should fail");
        } catch (IllegalStateException expected) {
            check(true, "draw commits once");
        }
        Poker.Deck fresh = new Poker.Deck(new Random(7), List.of());
        List<Poker.Card> kept = new ArrayList<>();
        for (int i = 0; i < 8; i++) kept.add(fresh.drawTop());
        Poker.Round allHeld = new Poker.Round(kept, fresh);
        Poker.BestHand value = allHeld.currentHoldValue();
        check(value != null && value.rank().equals(allHeld.playerBest.rank()),
                "keeping all eight shows the same value");
        allHeld.setHold(1, false);
        check(allHeld.currentHoldValue().rank().compareTo(value.rank()) <= 0,
                "holding fewer cards cannot raise the value");
        allHeld.setHold(0, false);
        allHeld.setHold(2, false);
        allHeld.setHold(3, false);
        allHeld.setHold(4, false);
        allHeld.setHold(5, false);
        allHeld.setHold(6, false);
        allHeld.setHold(7, false);
        check(allHeld.currentHoldValue() == null, "incomplete hand shows no value");
        allHeld.commitDraw(fresh);
        check(allHeld.playerBest != null, "draw completes the hand");
    }

    private static void finalFailure() throws Exception {
        PipeFlightGame game = new PipeFlightGame(new Random(2), new SpriteAtlas());
        for (int stage = 0; stage < 3; stage++) {
            game.playerY = PipeFlightGame.PLAY_TOP;
            game.pipeX = game.playerX;
            game.gapY = PipeFlightGame.PLAY_TOP + game.playerSize() + 10;
            game.step(0, 0, false);
            check(game.stage() == stage + 1, "card collision advances stage " + stage);
        }
        check(game.drowning && !game.gameOver, "third failure begins drowning");
        game.advanceDrowning(3.5);
        check(game.gameOver, "drowning ends after 3.5 seconds");
    }

    private static void goodburger() throws Exception {
        Path project = Path.of(LogicTest.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getParent().getParent();
        Path source = project.resolve("src/main/resources/goodburger");
        Path copy = Files.createTempDirectory("goodburger-test-");
        String[] files = {"menu.csv", "modifiers.csv", "orders.csv", "orders_items.csv",
                "users.csv", "favoriteorders.csv"};
        try {
            for (String name : files) Files.copy(source.resolve(name), copy.resolve(name));
            DataManager data = new DataManager(copy);
            String previousHome = System.getProperty("user.home");
            try {
                System.setProperty("user.home", copy.toString());
                Path installed = DataManager.resourceDir();
                check(new DataManager(installed).loadMenu().size() == 12,
                        "bundled jar resources seed a writable data directory");
                try (var copiedFiles = Files.list(installed)) {
                    check(copiedFiles.count() == 6, "all six bundled CSVs copied");
                }
            } finally {
                System.setProperty("user.home", previousHome);
            }
            List<MenuItem> menu = data.loadMenu();
            check(menu.size() == 12, "menu has twelve items");
            check(data.loadModifiers().size() == 11, "eleven modifiers loaded");
            check(Files.readAllLines(copy.resolve("menu.csv")).get(0).equals(
                    "id,name,category,price,calories,protein,carbs,fat,sodium,cateringOnly"),
                    "menu header contains all nutrition fields");
            for (MenuItem item : menu) {
                check(item.calories() > 0 && item.protein() >= 0 && item.carbs() >= 0
                        && item.fat() >= 0 && item.sodium() >= 0, "nutrition parsed for " + item.id());
            }
            for (String name : List.of("menu.csv", "modifiers.csv", "orders.csv")) {
                String contents = Files.readString(copy.resolve(name));
                check(!contents.contains("nutrition")
                        && !java.util.regex.Pattern.compile("[0-9A-F]{7}").matcher(contents).find(),
                        "no placeholder hex nutrition in " + name);
            }

            List<Order> orders = data.loadOrders();
            check(orders.size() == 10, "all ten orders loaded");
            Map<String, double[]> stored = new HashMap<>();
            for (String line : Files.readAllLines(copy.resolve("orders.csv")).subList(1, 11)) {
                String[] columns = line.split(",", -1);
                stored.put(columns[0], new double[] {Double.parseDouble(columns[3]),
                        Double.parseDouble(columns[4])});
            }
            System.out.println("orderId | stored | computed | OK");
            for (Order order : orders) {
                double computed = 0;
                for (OrderItem item : order.items()) computed += item.lineTotal(order.menu());
                double expected = stored.get(order.orderId())[0];
                check(Math.abs(computed - expected) < 0.005, "line sum " + order.orderId());
                check(Math.abs(order.total() - expected) < 0.005, "order total " + order.orderId());
                check(Math.abs(order.tax() - stored.get(order.orderId())[1]) < 0.005,
                        "order tax " + order.orderId());
                System.out.printf(java.util.Locale.ROOT, "%s | %.2f | %.2f | OK%n",
                        order.orderId(), expected, computed);
            }
            check(orders.get(0).totalCalories() == 770, "modifier calories included");
            check(orders.get(0).totalProtein() == 32, "protein derived from menu");
            check(data.loadFavoriteOrders().size() == 3, "favorites joined to orders");

            List<User> users = data.loadUsers();
            check(users.size() == 9, "nine users loaded");
            for (User user : users) check(user.passwordHash().matches("[0-9a-f]{64}"),
                    "seed password is SHA-256 hex");
            String digest = User.hashPassword("known");
            check(digest.equals(User.hashPassword("known")) && digest.matches("[0-9a-f]{64}"),
                    "hashPassword is deterministic lowercase SHA-256 hex");
            // Seed plaintext verified against the pre-migration users.csv
            // (pcd-audit-2026-11-02/submitted/extracted/data/users.csv, row 001).
            check(User.hashPassword("2Ao1$tObuCI*").equals(
                    "705c89bced4932353ede0f371f9b8788c3e3c6eda5dae753a53aab68a93cdc85"),
                    "hashPassword reproduces the stored SHA-256 seed digest for user 001");
            check(users.get(0).checkPassword("2Ao1$tObuCI*")
                    && !users.get(0).checkPassword("wrong-password"),
                    "user 001 verifies against its seed plaintext");
            User fresh = new User("T01", "Test User", "01/01/2000", "test@example.com",
                    User.hashPassword("known"), 0);
            data.saveUser(fresh);
            User reloadedUser = data.loadUsers().stream().filter(u -> u.id().equals("T01"))
                    .findFirst().orElseThrow();
            check(reloadedUser.checkPassword("known") && !reloadedUser.checkPassword("wrong"),
                    "saved user rehashes and verifies password");

            OrderManager manager = new OrderManager(menu);
            manager.newOrder("001", OrderType.PICKUP, "2026-11-02 12:00");
            MenuItem burger = new Menu(menu).findById("B01");
            Modifier bacon = burger.modifiers().stream().filter(m -> m.name().equals("Add bacon"))
                    .findFirst().orElseThrow();
            manager.addToOrder("B01", 2, List.of(bacon));
            manager.addToOrder("S01", 1, List.of());
            Order finished = manager.checkout();
            check(Math.abs(manager.calculateTotal() - finished.amountDue()) < 0.005,
                    "manager calculates amount due with tax");
            data.saveOrder(finished);
            Order loaded = data.loadOrders().stream()
                    .filter(o -> o.orderId().equals(finished.orderId())).findFirst().orElseThrow();
            check(loaded.items().size() == 2 && loaded.items().get(0).quantity() == 2
                    && loaded.items().get(0).modifiers().get(0).name().equals("Add bacon"),
                    "order lines and modifiers survive reload");
            check(Math.abs(loaded.total() - finished.total()) < 0.005
                    && Math.abs(loaded.tax() - finished.tax()) < 0.005,
                    "order money survives reload");
            data.saveFavoriteOrder("001", loaded);
            check(data.loadFavoriteOrders().size() == 4, "new favorite survives reload");
            manager.newOrder("001", OrderType.DINEIN, "2026-11-02 13:00");
            boolean rejected = false;
            try { manager.checkout(); } catch (IllegalStateException expected) { rejected = true; }
            check(rejected, "empty checkout rejected");

            for (Path file : Files.newDirectoryStream(project.resolve("src/main/java/rewards"), "*.java")) {
                if (file.getFileName().toString().equals("DataManager.java")) continue;
                String sourceText = Files.readString(file, StandardCharsets.UTF_8);
                for (String name : files) check(!sourceText.contains("\"" + name + "\""),
                        "only DataManager names GoodBurger CSV files: " + file);
            }
            System.out.println("All GoodBurger model checks passed.");
        } finally {
            try (var paths = Files.walk(copy)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        economy();
        foodie();
        poker();
        flight();
        gamble();
        discardDraw();
        pokerPause();
        finalFailure();
        goodburger();
        System.out.println("All JavaFX rewards and Pipe Flight logic checks passed.");
    }
}
