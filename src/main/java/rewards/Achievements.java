package rewards;

/** One-time achievement rewards for the points economy. */
public final class Achievements {
    public record Definition(String id, String name, int grant, String how) { }

    public static final Definition[] ALL = {
            new Definition("poker_win", "Card Shark", 200, "Win a poker showdown"),
            new Definition("streak_3", "Regular", 150, "Reach a 3-day login streak"),
            new Definition("redemptions_5", "Foodie", 200, "Redeem 5 rewards in total"),
    };

    private Achievements() { }
}
