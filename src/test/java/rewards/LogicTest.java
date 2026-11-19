package rewards;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
    }

    public static void main(String[] args) throws Exception {
        poker();
        Poker.Round round = Poker.Round.deal(new Random(1));
        var all = new java.util.HashSet<>(round.playerCards());
        all.addAll(round.dealerCards());
        check(round.playerCards().size() == 8 && round.dealerCards().size() == 8
                && all.size() == 16, "two unique eight-card hands from one deck");
        progress();
        System.out.println("All poker and rewards logic checks passed.");
    }
}
