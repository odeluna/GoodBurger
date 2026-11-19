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

/** Seven-card stud: two hidden cards, five shown cards, six betting streets, best five of seven. */
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

    /** Winner of a finished hand. PUSH = the pot is returned (matched shown cards or tied hands). */
    public enum Winner { PLAYER, DEALER, PUSH }

    /** The house's reply to a player action on a street. */
    public enum DealerPlay { CHECK, CALL, FOLD, RAISE }

    /**
     * One hand of seven-card stud against the house.
     *
     * <p>Both sides are dealt seven cards: the first two stay hidden, the other five
     * are shown one per street. The player posts an ante, acts first on each of the
     * six streets (check/call/raise/fold), and the house replies with a fixed
     * strategy. At showdown the best five of the seven cards wins the pot; if the
     * shown cards contain the same rank on both sides the pot is pushed back.
     */
    public static final class Stud {
        public static final int CARDS = 7;
        public static final int HOLE_CARDS = 2;
        public static final int STREETS = 6;
        public static final int ANTE = 50;
        public static final int BASE_BET = 50;

        private final List<Card> player = new ArrayList<>();
        private final List<Card> dealer = new ArrayList<>();
        private int street = 1;
        private int revealedUp;
        private int pot;
        private int stake;
        private int bankroll;
        private int facing;
        private int playerStreetBet;
        private int dealerStreetBet;
        private boolean dealerActed;
        private boolean playerAllIn;
        private boolean over;
        private HandRank playerRank;
        private HandRank dealerRank;
        private Winner winner;

        private Stud() { }

        /** Starts a hand from a fresh deck. {@code points} is the full balance; the ante is posted at once. */
        public static Stud deal(Random random, int points) {
            if (points < ANTE) throw new IllegalArgumentException("need at least " + ANTE + " points to ante");
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
        public int facing() { return facing; }
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

        /** True once a shown rank appears on both sides: the pot pushes at showdown. */
        public boolean isMatched() {
            Set<Integer> ranks = new HashSet<>();
            for (Card card : playerUpRevealed()) ranks.add(card.rank());
            for (Card card : dealerUpRevealed()) if (ranks.contains(card.rank())) return true;
            return false;
        }

        public boolean canCheck() { return !over && !playerAllIn && facing == 0; }
        public boolean canCall() { return !over && facing > 0; }
        public int callCost() { return Math.min(facing, bankroll); }
        public boolean canRaise() { return !over && !playerAllIn && bankroll >= facing + BASE_BET; }
        public int raiseCost() { return Math.min(facing + BASE_BET, bankroll); }

        /** Legal only while no one has bet this street. A strong house check-raises. */
        public void playerCheck() {
            inProgress();
            if (facing != 0) throw new IllegalStateException("must call " + facing + " before checking");
            DealerPlay play = dealerAct(street, 0);
            if (play == DealerPlay.RAISE) {
                dealerStreetBet = BASE_BET;
                pot += BASE_BET;
                facing = BASE_BET;
                dealerActed = true;
            } else {
                advanceStreet();
            }
        }

        /** Pays the current bet; pays the whole bankroll when it cannot cover. */
        public void playerCall() {
            inProgress();
            if (facing == 0) throw new IllegalStateException("nothing to call; check instead");
            int cost = callCost();
            bankroll -= cost;
            stake += cost;
            pot += cost;
            playerStreetBet += cost;
            if (bankroll == 0) playerAllIn = true;
            DealerPlay play = dealerAct(street, playerStreetBet - dealerStreetBet);
            if (play == DealerPlay.FOLD) { finish(Winner.PLAYER); return; }
            if (playerAllIn) { closeBettingToShowdown(); return; }
            applyDealerResponse(play);
        }

        /** Bets or raises by {@link #BASE_BET}. */
        public void playerRaise() {
            inProgress();
            if (!canRaise()) throw new IllegalStateException(
                    "cannot raise; call " + callCost() + " or fold");
            int cost = facing + BASE_BET;
            bankroll -= cost;
            stake += cost;
            pot += cost;
            playerStreetBet += cost;
            DealerPlay play = dealerAct(street, playerStreetBet - dealerStreetBet);
            if (play == DealerPlay.FOLD) { finish(Winner.PLAYER); return; }
            if (playerAllIn) { closeBettingToShowdown(); return; }
            applyDealerResponse(play);
        }

        /** Concedes the hand; the dealer takes the pot without a showdown. */
        public void playerFold() {
            inProgress();
            finish(Winner.DEALER);
        }

        private void applyDealerResponse(DealerPlay play) {
            if (play == DealerPlay.CALL) {
                int add = playerStreetBet - dealerStreetBet;
                dealerStreetBet += add;
                pot += add;
                advanceStreet();
            } else { // RAISE: call the open bet, then add the raise
                int add = playerStreetBet - dealerStreetBet + BASE_BET;
                dealerStreetBet += add;
                pot += add;
                facing = dealerStreetBet - playerStreetBet;
                dealerActed = true;
            }
        }

        /** The house acts once per street. */
        private DealerPlay dealerAct(int currentStreet, int dealerFacing) {
            if (dealerFacing == 0 && dealerActed) return DealerPlay.CHECK;
            return dealerPlay(currentStreet, dealerFacing, dealer);
        }

        private void advanceStreet() {
            if (playerAllIn) { closeBettingToShowdown(); return; }
            nextStreet();
        }

        private void nextStreet() {
            street++;
            if (street > STREETS) { showdown(); return; }
            revealedUp = street - 1;
            facing = 0;
            playerStreetBet = 0;
            dealerStreetBet = 0;
            dealerActed = false;
        }

        /** All-in: no more bets can be answered, so run the remaining streets to showdown. */
        private void closeBettingToShowdown() {
            while (!over) nextStreet();
        }

        private void showdown() {
            BestHand playerBest = bestFive(player);
            BestHand dealerBest = bestFive(dealer);
            playerRank = playerBest.rank();
            dealerRank = dealerBest.rank();
            if (isMatched()) winner = Winner.PUSH;
            else {
                int compare = playerRank.compareTo(dealerRank);
                winner = compare > 0 ? Winner.PLAYER : compare < 0 ? Winner.DEALER : Winner.PUSH;
            }
            over = true;
        }

        private void finish(Winner outcome) {
            winner = outcome;
            over = true;
        }

        private void inProgress() {
            if (over) throw new IllegalStateException("hand is over");
        }

        /** Net point change: the pot minus the stake on a win, the stake on a loss, zero on a push. */
        public int netDelta() {
            if (!over) throw new IllegalStateException("hand is not over");
            return switch (winner) {
                case PLAYER -> pot - stake;
                case DEALER -> -stake;
                case PUSH -> 0;
            };
        }

        /**
         * Fixed house strategy, derived only from the cards and the street so it is
         * deterministic and testable. Aggressive with three of a kind or better or a
         * high pair; calls small bets with any pair or ace high; limps early with
         * junk and folds it later.
         */
        public static DealerPlay dealerPlay(int street, int facing, List<Card> cards) {
            int strength = strengthOf(cards);
            boolean strong = strength >= 3000 || strength >= 1013;
            if (facing == 0) return strong ? DealerPlay.RAISE : DealerPlay.CHECK;
            if (strong) return facing <= BASE_BET ? DealerPlay.RAISE : DealerPlay.CALL;
            boolean medium = strength >= 1002 || strength == 14 || strength >= 26;
            if (medium) return facing <= 2 * BASE_BET ? DealerPlay.CALL : DealerPlay.FOLD;
            return facing == BASE_BET && street <= 2 ? DealerPlay.CALL : DealerPlay.FOLD;
        }

        private static int strengthOf(List<Card> cards) {
            if (cards.size() >= 5) {
                HandRank best = bestFive(cards).rank();
                return best.category() * 1000 + best.tieBreak().get(0);
            }
            int[] counts = new int[15];
            int pairRank = 0, top = 0, second = 0;
            for (Card card : cards) {
                counts[card.rank()]++;
                if (counts[card.rank()] == 2) pairRank = card.rank();
                top = Math.max(top, card.rank());
            }
            for (int rank = 14; rank >= 2; rank--) {
                if (counts[rank] > 0 && rank != top) { second = rank; break; }
            }
            return pairRank != 0 ? 1000 + pairRank : top + second;
        }
    }
}
