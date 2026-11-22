package rewards;

/** The reward economy is kept separate from rendering and game physics. */
public final class EconomyRules {
    public static final int DAILY_EARNINGS_CAP = 1_000;
    public static final int DAILY_BONUS_BASE = 100;
    public static final int DAILY_BONUS_PER_STREAK_DAY = 25;
    public static final int DAILY_BONUS_MAX = 300;

    private EconomyRules() { }
}
