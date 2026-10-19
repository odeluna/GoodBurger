package rewards;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
        double x = game.pipeX;
        game.update(1, new PipeFlightGame.Input(false, false, false, false, false));
        check(game.pipeX == x, "poker pauses flight");
        game.pokerAction();
        check(game.poker.revealed, "first action reveals showdown");
        game.pokerAction();
        check(game.poker == null, "second action resumes or advances");
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

    public static void main(String[] args) throws Exception {
        economy();
        foodie();
        poker();
        flight();
        gamble();
        pokerPause();
        finalFailure();
        System.out.println("All JavaFX rewards and Pipe Flight logic checks passed.");
    }
}
