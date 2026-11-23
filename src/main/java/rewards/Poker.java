package rewards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Card deck and hand evaluation shared by the Pipe Flight eight-card Round and the seven-card stud. */
public final class Poker {
    private Poker() { }

    public record Card(String suit, int rank) {
        public String value() {
            return switch (rank) {
                case 11 -> "J";
                case 12 -> "Q";
                case 13 -> "K";
                case 14 -> "A";
                default -> Integer.toString(rank);
            };
        }
        public String suitLetter() { return suit.substring(0, 1); }
        public boolean red() { return suit.equals("Hearts") || suit.equals("Diamonds"); }
    }

    public static final class Deck {
        private final List<Card> cards = new ArrayList<>();

        public Deck(Random random, List<Card> excluded) {
            for (String suit : new String[]{"Clubs", "Diamonds", "Hearts", "Spades"}) {
                for (int rank = 2; rank <= 14; rank++) {
                    Card card = new Card(suit, rank);
                    if (!excluded.contains(card)) cards.add(card);
                }
            }
            Collections.shuffle(cards, random);
        }

        public int size() { return cards.size(); }
        public boolean contains(Card card) { return cards.contains(card); }
        public Card drawTop() { return cards.remove(cards.size() - 1); }
        public Card drawBottom() { return cards.remove(0); }
        public void addBottom(Card card) { cards.add(0, card); }
        public void shuffle(Random random) { Collections.shuffle(cards, random); }
    }

    private static final String[] CATEGORY_NAMES = {
            "High Card", "One Pair", "Two Pair", "Three of a Kind", "Straight",
            "Flush", "Full House", "Four of a Kind", "Straight Flush"
    };

    public record HandRank(int category, List<Integer> tieBreak) implements Comparable<HandRank> {
        public String name() { return CATEGORY_NAMES[category]; }
        @Override public int compareTo(HandRank other) {
            int result = Integer.compare(category, other.category);
            if (result != 0) return result;
            for (int i = 0; i < Math.min(tieBreak.size(), other.tieBreak.size()); i++) {
                result = Integer.compare(tieBreak.get(i), other.tieBreak.get(i));
                if (result != 0) return result;
            }
            return 0;
        }
    }

    public record BestHand(List<Card> cards, HandRank rank) { }

    public static HandRank evaluateFive(List<Card> hand) {
        if (hand.size() != 5) throw new IllegalArgumentException("exactly five cards required");
        List<Integer> ranks = hand.stream().map(Card::rank).sorted(Comparator.reverseOrder()).toList();
        boolean flush = hand.stream().allMatch(card -> card.suit().equals(hand.get(0).suit()));
        Map<Integer, Integer> counts = new HashMap<>();
        for (int rank : ranks) counts.merge(rank, 1, Integer::sum);
        List<Integer> distinct = new ArrayList<>(counts.keySet());
        distinct.sort((a, b) -> {
            int count = Integer.compare(counts.get(b), counts.get(a));
            return count != 0 ? count : Integer.compare(b, a);
        });

        int straightHigh = 0;
        if (distinct.size() == 5) {
            if (ranks.get(0) - ranks.get(4) == 4) straightHigh = ranks.get(0);
            else if (ranks.equals(List.of(14, 5, 4, 3, 2))) straightHigh = 5;
        }

        int category;
        if (straightHigh > 0 && flush) category = 8;
        else if (counts.get(distinct.get(0)) == 4) category = 7;
        else if (counts.get(distinct.get(0)) == 3 && counts.get(distinct.get(1)) == 2) category = 6;
        else if (flush) category = 5;
        else if (straightHigh > 0) category = 4;
        else if (counts.get(distinct.get(0)) == 3) category = 3;
        else if (counts.get(distinct.get(0)) == 2 && counts.get(distinct.get(1)) == 2) category = 2;
        else if (counts.get(distinct.get(0)) == 2) category = 1;
        else category = 0;

        List<Integer> tie = (category == 8 || category == 4) ? List.of(straightHigh)
                : (category == 7 || category == 6 || category == 3 || category == 2 || category == 1)
                ? List.copyOf(distinct) : ranks;
        return new HandRank(category, tie);
    }

    public static BestHand bestFive(List<Card> cards) {
        if (cards.size() < 5) throw new IllegalArgumentException("at least five cards required");
        List<Card> best = null;
        HandRank bestRank = null;
        for (int a = 0; a < cards.size() - 4; a++)
            for (int b = a + 1; b < cards.size() - 3; b++)
                for (int c = b + 1; c < cards.size() - 2; c++)
                    for (int d = c + 1; d < cards.size() - 1; d++)
                        for (int e = d + 1; e < cards.size(); e++) {
                            List<Card> hand = List.of(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e));
                            HandRank rank = evaluateFive(hand);
                            if (bestRank == null || rank.compareTo(bestRank) > 0) {
                                best = hand;
                                bestRank = rank;
                            }
                        }
        return new BestHand(best, bestRank);
    }

    public static final class Round {
        public final List<Card> playerCards;
        public final List<Card> dealerCards = new ArrayList<>();
        public BestHand playerBest;
        public BestHand dealerBest;
        public boolean revealed;
        public boolean lost;
        public boolean powerUsed;
        public boolean upgradeDue;
        public String outcome = "";

        public Round(List<Card> playerCards, Deck deck) {
            this.playerCards = List.copyOf(playerCards);
            for (int i = 0; i < 8; i++) dealerCards.add(deck.drawTop());
            playerBest = bestFive(this.playerCards);
            dealerBest = bestFive(dealerCards);
        }
    }

    public enum Winner { PLAYER, DEALER, PUSH }

    /**
     * One hand of seven-card stud against the house.
     *
     * <p>Both sides are dealt seven cards: the first two stay hidden, the other five
     * are shown one per street. The house only deals the cards and posts its ante;
     * it never bets, so every decision is the player's. The player posts an ante
     * and on each of the six streets may check, raise, or fold, and the next card
     * is dealt straight away. There is no call: a check is usable at most twice
     * per hand, and every check or raise flips one of the player's hole cards face
     * up. A raise adds {@link #BASE_BET} to the pot and to the player's stake.
     * Winning pays the full pot; losing at showdown costs the full stake; folding
     * costs only half of it. A
     * raise that empties the bankroll is all-in and runs the remaining streets to
     * showdown. If the best five-card hands tie exactly the pot is pushed. A fold
     * runs the hand out: the remaining streets are dealt and every card is revealed
     * before the hand is over.
     */
    public static final class Stud {
        public static final int CARDS = 7;
        public static final int HOLE_CARDS = 2;
        public static final int STREETS = 6;
        public static final int ANTE = 50;
        /** Minimum account balance to sit down and play; the ante itself stays {@value #ANTE}. */
        public static final int MIN_POINTS = 250;
        public static final int BASE_BET = 50;
        public static final int MAX_CHECKS = 2;

        private final List<Card> player = new ArrayList<>();
        private final List<Card> dealer = new ArrayList<>();
        private int street = 1;
        private int revealedUp;
        private int pot;
        private int stake;
        private int bankroll;
        private boolean playerAllIn;
        private boolean over;
        private HandRank playerRank;
        private HandRank dealerRank;
        private Winner winner;
        private int checksUsed;
        private int holeRevealed;
        private boolean folded;

        private Stud() { }

        /** Starts a hand from a fresh deck. {@code points} is the full balance; the ante is posted at once. */
        public static Stud deal(Random random, int points) {
            if (points < MIN_POINTS) throw new IllegalArgumentException("need at least " + MIN_POINTS + " points to play");
            Stud table = new Stud();
            List<Card> deck = new ArrayList<>();
            for (String suit : new String[]{"Hearts", "Diamonds", "Clubs", "Spades"})
                for (int rank = 2; rank <= 14; rank++) deck.add(new Card(suit, rank));
            Collections.shuffle(deck, random);
            for (int i = 0; i < CARDS; i++) {
                table.player.add(deck.get(i));
                table.dealer.add(deck.get(CARDS + i));
            }
            table.open(points);
            return table;
        }

        /** Fixed hands (first two cards are the hidden ones); for tests and previews. */
        static Stud withCards(List<Card> player, List<Card> dealer, int points) {
            if (player.size() != CARDS || dealer.size() != CARDS)
                throw new IllegalArgumentException("stud hands are seven cards");
            Set<Card> seen = new HashSet<>();
            for (Card card : player) if (!seen.add(card)) throw new IllegalArgumentException("duplicate card " + card);
            for (Card card : dealer) if (!seen.add(card)) throw new IllegalArgumentException("duplicate card " + card);
            Stud table = new Stud();
            table.player.addAll(player);
            table.dealer.addAll(dealer);
            table.open(points);
            return table;
        }

        private void open(int points) {
            pot = 2 * ANTE;
            stake = ANTE;
            bankroll = points - ANTE;
            street = 1;
        }

        public int street() { return street; }
        public int pot() { return pot; }
        public int stake() { return stake; }
        public int bankroll() { return bankroll; }
        public boolean playerAllIn() { return playerAllIn; }
        public boolean isOver() { return over; }
        public Winner winner() {
            if (!over) throw new IllegalStateException("hand is not over");
            return winner;
        }
        public HandRank playerRank() { return playerRank; }
        public HandRank dealerRank() { return dealerRank; }
        public List<Card> playerCards() { return List.copyOf(player); }
        public List<Card> dealerCards() { return List.copyOf(dealer); }
        public List<Card> playerHole() { return List.of(player.get(0), player.get(1)); }
        public List<Card> dealerHole() { return List.of(dealer.get(0), dealer.get(1)); }
        public List<Card> playerUpRevealed() { return List.copyOf(player.subList(HOLE_CARDS, HOLE_CARDS + revealedUp)); }
        public List<Card> dealerUpRevealed() { return List.copyOf(dealer.subList(HOLE_CARDS, HOLE_CARDS + revealedUp)); }
        public int checksLeft() { return MAX_CHECKS - checksUsed; }
        public List<Card> playerHoleRevealed() { return List.copyOf(player.subList(0, holeRevealed)); }
        public boolean folded() { return folded; }

        public boolean canCheck() { return !over && !playerAllIn && checksUsed < MAX_CHECKS; }
        public boolean canRaise() { return !over && !playerAllIn && bankroll >= BASE_BET; }
        public int raiseCost() { return Math.min(BASE_BET, bankroll); }

        /** Checks the street for free; the next card is dealt straight away. Usable at most twice per hand. */
        public void playerCheck() {
            inProgress();
            if (checksUsed >= MAX_CHECKS) throw new IllegalStateException("checks are used up for this hand");
            checksUsed++;
            revealHoleCard();
            advanceStreet();
        }

        /** Raises the street by {@link #BASE_BET}, adding it to the pot and the player's stake. */
        public void playerRaise() {
            inProgress();
            if (!canRaise()) throw new IllegalStateException("cannot raise; fold instead");
            revealHoleCard();
            bankroll -= BASE_BET;
            stake += BASE_BET;
            pot += BASE_BET;
            if (bankroll == 0) {
                playerAllIn = true;
                closeBettingToShowdown();
                return;
            }
            advanceStreet();
        }

        /** Concedes the betting; the hand is still run out so all cards are revealed. */
        public void playerFold() {
            inProgress();
            folded = true;
            runOut(Winner.DEALER);
        }

        /** Check and raise each flip one hole card face up, until both are shown. */
        private void revealHoleCard() {
            if (holeRevealed < HOLE_CARDS) holeRevealed++;
        }

        private void advanceStreet() {
            nextStreet();
        }

        private void nextStreet() {
            street++;
            if (street > STREETS) {
                street = STREETS;
                showdown();
                return;
            }
            revealedUp = street - 1;
        }

        /** All-in: no more bets can be answered, so run the remaining streets to showdown. */
        private void closeBettingToShowdown() {
            while (!over) nextStreet();
        }

        /** Early stop of the betting: the remaining streets are still dealt so
         *  every card is revealed, and the fixed outcome wins. */
        private void runOut(Winner fixedWinner) {
            while (!over) nextStreet();
            winner = fixedWinner;
        }

        private void showdown() {
            BestHand playerBest = bestFive(player);
            BestHand dealerBest = bestFive(dealer);
            playerRank = playerBest.rank();
            dealerRank = dealerBest.rank();
            int compare = playerRank.compareTo(dealerRank);
            winner = compare > 0 ? Winner.PLAYER : compare < 0 ? Winner.DEALER : Winner.PUSH;
            over = true;
        }

        private void inProgress() {
            if (over) throw new IllegalStateException("hand is over");
        }

        /** Net point change: the full pot on a win, the full stake on a loss, half the stake on a fold, zero on a push. */
        public int netDelta() {
            if (!over) throw new IllegalStateException("hand is not over");
            return switch (winner) {
                case PLAYER -> pot;
                case DEALER -> folded ? -stake / 2 : -stake;
                case PUSH -> 0;
            };
        }
    }
}
