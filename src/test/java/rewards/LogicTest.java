package rewards;

import edu.utsa.cs3443.goodburger.model.MenuManager;
import edu.utsa.cs3443.goodburger.model.MenuItem;
import edu.utsa.cs3443.goodburger.model.DataManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

    private static void stud() {
        Poker.Stud table = Poker.Stud.deal(new Random(7), 500);
        Set<Poker.Card> all = new HashSet<>(table.playerCards());
        all.addAll(table.dealerCards());
        check(table.playerCards().size() == 7 && table.dealerCards().size() == 7
                && all.size() == 14, "two unique seven-card hands from one deck");
        check(table.pot() == 100 && table.stake() == 50 && table.bankroll() == 450, "ante posts both sides");
        check(table.street() == 1 && table.playerUpRevealed().isEmpty()
                && table.dealerUpRevealed().isEmpty(), "nothing shown before street one");
        check(table.canCheck() && !table.canCall() && table.canRaise(), "street one starts free to check or bet");

        while (!table.isOver()) {
            if (table.canCheck()) table.playerCheck();
            else table.playerCall();
        }
        check(table.playerUpRevealed().size() == 5 && table.dealerUpRevealed().size() == 5
                && table.playerRank() != null && table.dealerRank() != null,
                "check/call runs all six streets to a showdown");

        check(Poker.Stud.dealerPlay(1, 0, List.of(card("Hearts", 14), card("Spades", 14)))
                == Poker.DealerPlay.RAISE, "strong hand check-raises");
        check(Poker.Stud.dealerPlay(1, 100, List.of(card("Hearts", 14), card("Spades", 14)))
                == Poker.DealerPlay.CALL, "strong hand calls an oversize bet");
        check(Poker.Stud.dealerPlay(1, 50, List.of(card("Hearts", 2), card("Spades", 3)))
                == Poker.DealerPlay.CALL, "junk limps early streets");
        check(Poker.Stud.dealerPlay(3, 50, List.of(card("Hearts", 2), card("Spades", 3)))
                == Poker.DealerPlay.FOLD, "junk folds a later street");

        Poker.Stud allIn = Poker.Stud.withCards(
                List.of(card("Hearts", 14), card("Spades", 13), card("Hearts", 5), card("Diamonds", 11),
                        card("Clubs", 12), card("Spades", 7), card("Diamonds", 3)),
                List.of(card("Hearts", 2), card("Clubs", 3), card("Diamonds", 9), card("Spades", 8),
                        card("Hearts", 6), card("Clubs", 13), card("Spades", 4)),
                450);
        while (!allIn.isOver()) {
            if (allIn.facing() == 0) allIn.playerRaise();
            else allIn.playerCall();
        }
        check(allIn.winner() == Poker.Winner.PLAYER && allIn.playerRank() == null,
                "dealer folds a weak hand before showdown");
        check(allIn.street() <= 3, "junk folds by street three");
        check(allIn.netDelta() > 0 && allIn.pot() > allIn.stake(), "winning pot pays more than the stake");

        Poker.Stud matched = Poker.Stud.withCards(
                List.of(card("Clubs", 7), card("Diamonds", 2), card("Hearts", 13), card("Spades", 12),
                        card("Hearts", 4), card("Clubs", 11), card("Diamonds", 9)),
                List.of(card("Spades", 3), card("Hearts", 8), card("Diamonds", 13), card("Clubs", 5),
                        card("Spades", 10), card("Diamonds", 14), card("Spades", 2)),
                500);
        while (!matched.isOver()) {
            if (matched.canCheck()) matched.playerCheck();
            else matched.playerCall();
        }
        check(matched.isMatched() && matched.winner() == Poker.Winner.PUSH,
                "matched shown ranks push even against a better hand");
        check(matched.netDelta() == 0, "push returns the stake");
        check(matched.playerRank() != null && matched.dealerRank() != null,
                "showdown still evaluates both hands");

        Poker.Stud win = Poker.Stud.withCards(
                List.of(card("Hearts", 14), card("Spades", 13), card("Hearts", 12), card("Spades", 11),
                        card("Hearts", 10), card("Spades", 2), card("Hearts", 6)),
                List.of(card("Clubs", 9), card("Diamonds", 8), card("Diamonds", 7), card("Spades", 5),
                        card("Clubs", 4), card("Diamonds", 3), card("Diamonds", 14)),
                500);
        while (!win.isOver()) {
            if (win.canCheck()) win.playerCheck();
            else win.playerCall();
        }
        check(win.winner() == Poker.Winner.PLAYER && win.playerRank().category() == 4,
                "best five of seven decides the showdown");
        check(win.netDelta() == 50, "untouched pot pays the winner the full pot");

        Poker.Stud first = runStud(new Random(11), 500);
        Poker.Stud second = runStud(new Random(11), 500);
        check(first.playerCards().equals(second.playerCards())
                && first.dealerCards().equals(second.dealerCards()), "seeded stud deals are stable");
        check(first.pot() == second.pot() && first.winner() == second.winner()
                && first.netDelta() == second.netDelta(), "identical play gives identical payouts");
    }

    private static Poker.Stud runStud(Random random, int points) {
        Poker.Stud table = Poker.Stud.deal(random, points);
        while (!table.isOver()) {
            if (table.canCheck()) table.playerCheck();
            else table.playerCall();
        }
        return table;
    }

    private static void progress() throws Exception {
        Path file = Files.createTempDirectory("poker-progress").resolve("progress.properties");
        Files.writeString(file, "version=2\npoints=800\nredeemed.0=1\npokerWins=0\n"
                + "bestRun=12\nunlockedAchievements=first_play,pipes_10\n");
        TestClock clock = new TestClock();
        ProgressStore store = new ProgressStore(file, clock);
        check(store.points() == 800 && store.redeemedCount(0) == 1, "existing rewards load");
        store.recordPokerWin();
        store.recordPokerWin();
        check(store.claimDailyBonus() == 100, "daily bonus");
        check(store.claimDailyBonus() == 0, "daily bonus once");
        ProgressStore reloaded = new ProgressStore(file, clock);
        check(reloaded.points() == 1100 && reloaded.pokerWins() == 2, "wins persist and grant once");
        check(reloaded.hasUnlocked("poker_win"), "first-win achievement");
        check(reloaded.tradeIn(0) == 150 && reloaded.redeem(0), "food rewards still work");
        check(reloaded.tradeInFull(0) == 300, "full trade-in refunds the whole cost");
        check(reloaded.points() == 1250 && reloaded.redeemedCount(0) == 0, "full trade-in empties the count");
        check(reloaded.tradeInFull(0) == 0, "full trade-in with nothing redeemed returns nothing");
        check(reloaded.redeem(0), "redemption for the consume test");
        check(reloaded.points() == 950 && reloaded.redeemedCount(0) == 1, "redemption charges the full cost");
        reloaded.consumeRedemption(0);
        check(reloaded.points() == 950 && reloaded.redeemedCount(0) == 0, "consume drops the count and moves no points");
        ProgressStore afterConsume = new ProgressStore(file, clock);
        check(afterConsume.points() == 950 && afterConsume.redeemedCount(0) == 0, "consume persists across reloads");
        reloaded.adjustPoints(-100);
        check(reloaded.points() == 850, "poker loss deducts points");
        reloaded.adjustPoints(-9000);
        check(reloaded.points() == 0, "balance floors at zero");
    }

    /**
     * menu.csv round trip: a quoted description containing commas must survive the parse, the photo
     * column must land on the item, and a legacy 10-column row must still load with empty photo and
     * description (old files must keep working).
     */
    private static void menuCsv() throws Exception {
        Path dir = Files.createTempDirectory("goodburger-menu");
        Files.writeString(dir.resolve("menu.csv"), String.join("\n",
                "id,name,category,price,calories,protein,carbs,fat,sodium,cateringOnly,photo,description",
                "B01,Rowdy Burger,ENTREE,6.99,650,32,45,35,980,false,01B_RowdyBurger.png,\"A classic, with commas, inside quotes.\"",
                "S01,Tater Tots,SIDE,2.49,320,4,41,15,230,false",
                ""));
        Files.writeString(dir.resolve("modifiers.csv"),
                "itemId,name,extraPrice,extraCalories,extraProtein,extraCarbs,extraFat,extraSodium\n"
                + "B01,Extra Cheese,1.00,100,4,2,9,200\n");
        MenuManager menu = new DataManager(dir).loadMenu();
        MenuItem rowdy = menu.findById("B01");
        check(rowdy != null, "item B01 loaded");
        check(rowdy.getPhoto().equals("01B_RowdyBurger.png"), "photo column parsed: " + rowdy.getPhoto());
        check(rowdy.getDescription().equals("A classic, with commas, inside quotes."),
                "quoted description with commas parsed: " + rowdy.getDescription());
        check(rowdy.getPrice() == 6.99 && rowdy.getNutrition().getCalories() == 650
                && !rowdy.isCateringOnly(), "price, nutrition and catering flag still parsed");
        MenuItem tots = menu.findById("S01");
        check(tots != null && tots.getPhoto().isEmpty() && tots.getDescription().isEmpty(),
                "legacy 10-column row loads with empty photo/description");
    }

    public static void main(String[] args) throws Exception {
        poker();
        stud();
        progress();
        menuCsv();
        System.out.println("All poker and rewards logic checks passed.");
    }
}
