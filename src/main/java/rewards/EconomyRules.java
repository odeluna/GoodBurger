package rewards;

/** The reward economy is kept separate from rendering and game physics. */
public final class EconomyRules {
    public static final int STARTING_POINTS = 500;
    public static final int PLAY_COST = 500;
    public static final int DAILY_EARNINGS_CAP = 2_000;
    public static final int POINTS_PER_PIPE = 1;
    public static final int DAILY_BONUS_BASE = 100;
    public static final int DAILY_BONUS_PER_STREAK_DAY = 25;
    public static final int DAILY_BONUS_MAX = 300;

    private EconomyRules() { }
}
