package rewards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Card deck and five-of-eight poker evaluation translated from deckofcards and poker_round.go. */
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

    public enum Phase { DISCARD, REVEAL }

    public static final class Round {
        public List<Card> playerCards;
        public final List<Card> dealerCards = new ArrayList<>();
        public final List<Boolean> hold = new ArrayList<>();
        public Phase phase = Phase.DISCARD;
        public BestHand playerBest;
        public BestHand dealerBest;
        public boolean revealed;
        public boolean lost;
        public boolean powerUsed;
        public boolean upgradeDue;
        public String outcome = "";

        public Round(List<Card> playerCards, Deck deck) {
            this.playerCards = List.copyOf(playerCards);
            for (int i = 0; i < 8; i++) {
                dealerCards.add(deck.drawTop());
                hold.add(true);
            }
            playerBest = bestFive(this.playerCards);
            dealerBest = bestFive(dealerCards);
        }

        public void setHold(int index, boolean held) {
            if (phase != Phase.DISCARD) throw new IllegalStateException("cards are locked");
            hold.set(index, held);
        }

        /** Replaces every discarded card with a fresh deck draw and locks the hand. */
        public void commitDraw(Deck deck) {
            if (phase != Phase.DISCARD) throw new IllegalStateException("draw already committed");
            List<Card> hand = new ArrayList<>(8);
            for (int i = 0; i < playerCards.size(); i++)
                hand.add(hold.get(i) ? playerCards.get(i) : deck.drawTop());
            playerCards = List.copyOf(hand);
            playerBest = bestFive(playerCards);
            phase = Phase.REVEAL;
        }

        /** Best five of the cards being kept, or null while the hand is incomplete. */
        public BestHand currentHoldValue() {
            if (phase != Phase.DISCARD) return playerBest;
            List<Card> held = new ArrayList<>();
            for (int i = 0; i < playerCards.size(); i++)
                if (hold.get(i)) held.add(playerCards.get(i));
            return held.size() >= 5 ? bestFive(held) : null;
        }
    }
}
