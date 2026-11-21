package rewards;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Persists the balance, redemptions, streaks, achievements, and stats, plus the
 * local-calendar-day earnings cap. Legacy v1 files (balance/redemptions only) load
 * unchanged; new fields default to empty and the file is rewritten as v2 on save.
 */
public final class ProgressStore {
    public enum StartResult { STARTED, NOT_ENOUGH_POINTS, DAILY_CAP_REACHED }
    /** Fired on the FX thread so the UI can show one-shot reward toasts. */
    public record AchievementEvent(Achievements.Definition definition, int grant) { }
    /** A reward handed out outside per-pipe earnings: login, achievements, or a double. */
    public record EarnResult(int baseAward, int dailyBonus, int doubleBonus,
                             int achievements, List<AchievementEvent> unlocked) { }

    public static final String[] REWARD_NAMES = {"Soda", "Fries", "Salad"};
    public static final int[] REWARD_COSTS = {300, 500, 1000};
    static final int TRADE_BACK_PERCENT = 50;
    /** Minimum pipes in a run before the double-or-nothing coin flip is offered. */
    public static final int GAMBLE_MIN_PIPES = 3;
    private static final int FILE_VERSION = 2;

    private final Path file;
    private final Clock clock;
    private final LocalDate today;
    private boolean loadedExisting;
    private int points = EconomyRules.STARTING_POINTS;
    private int earnedToday;
    private LocalDate earnedDate;
    private final int[] redeemed = new int[REWARD_NAMES.length];
    private int streakDays;
    private LocalDate lastBonusDate;
    private int bestRun;
    private int gamesPlayed;
    private int pokerWins;
    private boolean doubledToday;
    private final List<String> unlocked = new ArrayList<>();
    private Consumer<AchievementEvent> onAchievement;
    private Consumer<Integer> onBalanceChanged;
    private Supplier<LocalDate> gambleDeadline;

    public ProgressStore(Path file, Clock clock) throws IOException {
        this.file = file;
        this.clock = clock;
        this.today = LocalDate.now(clock);
        this.earnedDate = today;
        load();
    }

    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".local", "share",
                "rewards-javafx", "progress.properties");
    }

    public synchronized int points() { return points; }
    public synchronized int earnedToday() { return earnedToday; }
    public synchronized int remainingToday() {
        return Math.max(0, EconomyRules.DAILY_EARNINGS_CAP - earnedToday);
    }
    public synchronized int redeemedCount(int index) { return redeemed[index]; }
    public synchronized int totalRedemptions() {
        int total = 0;
        for (int count : redeemed) total += count;
        return total;
    }
    public synchronized boolean canRedeem(int index) { return points >= REWARD_COSTS[index]; }
    public synchronized boolean hasUnlocked(String id) { return unlocked.contains(id); }
    public synchronized List<Achievements.Definition> unlockedDefinitions() {
        List<Achievements.Definition> granted = new ArrayList<>();
        for (Achievements.Definition definition : Achievements.ALL)
            if (unlocked.contains(definition.id())) granted.add(definition);
        return List.copyOf(granted);
    }
    public synchronized int streakDays() { return streakDays; }
    private LocalDate liveDay() { return LocalDate.now(clock); }
    /** Points granted for the first login of the current local day. */
    public synchronized int dailyBonusForToday() {
        if (lastBonusDate != null && !lastBonusDate.equals(liveDay())) streakDays = 0;
        return Math.min(EconomyRules.DAILY_BONUS_MAX,
                EconomyRules.DAILY_BONUS_BASE + streakDays * EconomyRules.DAILY_BONUS_PER_STREAK_DAY);
    }
    public synchronized int bestRun() { return bestRun; }
    public synchronized int gamesPlayed() { return gamesPlayed; }
    public synchronized int pokerWins() { return pokerWins; }
    /** True when today's per-pipe earnings already reach the daily cap. */
    public synchronized boolean perPipeCapReached() { return earnedToday >= EconomyRules.DAILY_EARNINGS_CAP; }
    /** True when the login bonus for the current local day has already been claimed. */
    public synchronized boolean bonusClaimedToday() {
        return lastBonusDate != null && lastBonusDate.equals(liveDay());
    }
    /** Whether a coin flip can still be taken on the last run (one per day). */
    public synchronized boolean gambleOpen() {
        return gambleDeadline != null && !gambleDeadline.get().isBefore(liveDay()) && !doubledToday;
    }
    public synchronized double tradeBackValue(int index) {
        return REWARD_COSTS[index] * TRADE_BACK_PERCENT / 100.0;
    }

    /** UI hook: show achievement toasts on the FX thread. */
    public synchronized void onAchievementAwarded(Consumer<AchievementEvent> consumer) {
        onAchievement = consumer;
    }
    /** UI hook: keep the gamble window tied to the app's own local date. */
    public synchronized void setGambleDeadlineSupplier(Supplier<LocalDate> deadline) {
        gambleDeadline = deadline;
    }
    /** True when the store file existed at construction, so callers can tell a fresh store apart. */
    public synchronized boolean hasSavedFile() {
        return loadedExisting;
    }
    /**
     * Replaces the balance. The rewards page calls this at startup so the store
     * starts at the signed-in user's points from users.csv.
     */
    public synchronized void setBalance(int newPoints) throws IOException {
        int original = points;
        points = Math.max(0, newPoints);
        try {
            save();
        } catch (IOException error) {
            points = original;
            throw error;
        }
    }
    /**
     * UI hook: after every successful save, the new balance is passed to the
     * listener so the caller can mirror it into users.csv for the signed-in user.
     */
    public synchronized void onBalanceChanged(Consumer<Integer> listener) {
        onBalanceChanged = listener;
    }

    /**
     * Daily login bonus: +100 base, +25 per consecutive day, capped at 300.
     * Skipped when the day was already claimed; the streak resets on a missed day.
     */
    public synchronized int claimDailyBonus() throws IOException {
        LocalDate live = liveDay();
        if (lastBonusDate != null && lastBonusDate.equals(live)) return 0;
        if (lastBonusDate != null && !lastBonusDate.equals(live.minusDays(1))) streakDays = 0;
        int bonus = Math.min(EconomyRules.DAILY_BONUS_MAX,
                EconomyRules.DAILY_BONUS_BASE + streakDays * EconomyRules.DAILY_BONUS_PER_STREAK_DAY);
        streakDays++;
        lastBonusDate = live;
        List<AchievementEvent> fired = new ArrayList<>();
        if (streakDays >= 3) grant(DefinitionRef.STREAK_3, fired);
        int achievements = 0;
        for (AchievementEvent event : fired) achievements += event.grant();
        points += bonus + achievements;
        save();
        if (!fired.isEmpty()) fire(fired);
        return bonus;
    }

    /**
     * Adds or removes points directly, e.g. when a poker showdown settles the
     * pot against the balance. The balance never drops below zero.
     */
    public synchronized void adjustPoints(int delta) throws IOException {
        int original = points;
        points = Math.max(0, points + delta);
        try {
            save();
        } catch (IOException error) {
            points = original;
            throw error;
        }
    }

    /** Resets the per-pipe counter and the one-per-day gamble when the local day changes. */
    public synchronized void rollDay() throws IOException {
        LocalDate live = liveDay();
        if (!earnedDate.equals(live)) {
            earnedDate = live;
            earnedToday = 0;
            doubledToday = false;
            save();
        }
    }

    public synchronized StartResult startPlay() throws IOException {
        rollDay();
        if (points < EconomyRules.PLAY_COST) return StartResult.NOT_ENOUGH_POINTS;
        if (perPipeCapReached()) return StartResult.DAILY_CAP_REACHED;
        int previous = points;
        points -= EconomyRules.PLAY_COST;
        try {
            save();
        } catch (IOException error) {
            points = previous;
            throw error;
        }
        return StartResult.STARTED;
    }

    /**
     * Settles a finished run once: per-pipe points (daily-cap limited) plus any
     * one-time achievements. Achievements count toward the daily cap; the
     * login bonus does not.
     */
    public synchronized EarnResult finishPlay(int pipesPassed) throws IOException {
        if (pipesPassed < 0) throw new IllegalArgumentException("pipes must be nonnegative");
        int previousPoints = points, previousEarned = earnedToday,
                previousGames = gamesPlayed, previousBest = bestRun,
                previousStreak = streakDays;
        LocalDate previousBonusDate = lastBonusDate;
        List<String> previousUnlocked = List.copyOf(unlocked);
        gamesPlayed++;
        bestRun = Math.max(bestRun, pipesPassed);
        int bonus = claimDailyBonus();
        int base = (int) Math.min((long) remainingToday(), (long) pipesPassed * EconomyRules.POINTS_PER_PIPE);
        List<AchievementEvent> fired = new ArrayList<>();
        grant(DefinitionRef.FIRST_PLAY, fired);
        if (pipesPassed >= 10) grant(DefinitionRef.PIPES_10, fired);
        if (pipesPassed >= 25) grant(DefinitionRef.PIPES_25, fired);
        if (totalRedemptions() >= 5) grant(DefinitionRef.REDEMPTIONS_5, fired);
        int achievements = 0;
        for (AchievementEvent event : fired) achievements += event.grant();
        int earned = base + achievements;
        if (earned > 0) {
            earnedToday += earned;
            points += earned;
        }
        try {
            save();
        } catch (IOException error) {
            points = previousPoints;
            earnedToday = previousEarned;
            gamesPlayed = previousGames;
            bestRun = previousBest;
            streakDays = previousStreak;
            lastBonusDate = previousBonusDate;
            unlocked.clear();
            unlocked.addAll(previousUnlocked);
            throw error;
        }
        if (!fired.isEmpty()) fire(fired);
        return new EarnResult(base, bonus, 0, achievements, fired);
    }

    /** First poker win of the account; one-time grant, exempt from the daily cap. */
    public synchronized List<AchievementEvent> recordPokerWin() throws IOException {
        pokerWins++;
        List<AchievementEvent> fired = new ArrayList<>();
        grant(DefinitionRef.POKER_WIN, fired);
        int grantTotal = 0;
        for (AchievementEvent event : fired) grantTotal += event.grant();
        if (grantTotal > 0) {
            points += grantTotal;
            try {
                save();
            } catch (IOException error) {
                points -= grantTotal;
                pokerWins--;
                throw error;
            }
            fire(fired);
        }
        return fired;
    }

    /**
     * Double-or-nothing: risk the last run's earnings for a coin flip. Win doubles
     * them (exempt from the daily cap); lose forfeits the per-pipe points, any
     * achievement points the run earned, and the daily login bonus. One gamble per day.
     */
    public synchronized EarnResult recordGamble(boolean win, EarnResult previous) throws IOException {
        if (!gambleOpen() || previous.baseAward() < GAMBLE_MIN_PIPES)
            throw new IllegalStateException("gamble is not open for this run");
        int atStake = previous.baseAward() + previous.achievements();
        if (win) {
            points += atStake;
        } else {
            points = Math.max(0, points - (atStake + previous.dailyBonus()));
            earnedToday = Math.max(0, earnedToday - atStake);
        }
        doubledToday = true;
        try {
            save();
        } catch (IOException error) {
            doubledToday = false;
            if (win) points -= atStake;
            throw error;
        }
        return new EarnResult(previous.baseAward(), 0,
                win ? atStake : 0, 0, List.of());
    }

    public synchronized boolean redeem(int index) throws IOException {
        if (index < 0 || index >= REWARD_NAMES.length) throw new IndexOutOfBoundsException(index);
        if (!canRedeem(index)) return false;
        points -= REWARD_COSTS[index];
        redeemed[index]++;
        List<AchievementEvent> fired = new ArrayList<>();
        if (totalRedemptions() >= 5) grant(DefinitionRef.REDEMPTIONS_5, fired);
        int grantTotal = 0;
        for (AchievementEvent event : fired) grantTotal += event.grant();
        int previousPoints = points;
        points += grantTotal;
        try {
            save();
        } catch (IOException error) {
            points = previousPoints;
            redeemed[index]--;
            throw error;
        }
        if (grantTotal > 0) fire(fired);
        return true;
    }

    /** Converting a redeemed reward back for half its cost. */
    public synchronized int tradeIn(int index) throws IOException {
        if (index < 0 || index >= REWARD_NAMES.length) throw new IndexOutOfBoundsException(index);
        if (redeemed[index] == 0) return 0;
        int refund = REWARD_COSTS[index] * TRADE_BACK_PERCENT / 100;
        points += refund;
        redeemed[index]--;
        try {
            save();
        } catch (IOException error) {
            points -= refund;
            redeemed[index]++;
            throw error;
        }
        return refund;
    }

    /**
     * Taking a redeemed reward back for its full cost: the whole redemption price
     * returns to the balance and the count drops by one. This is what the cart's
     * minus button does when it removes a redeemed line.
     */
    public synchronized int tradeInFull(int index) throws IOException {
        if (index < 0 || index >= REWARD_NAMES.length) throw new IndexOutOfBoundsException(index);
        if (redeemed[index] == 0) return 0;
        int refund = REWARD_COSTS[index];
        points += refund;
        redeemed[index]--;
        try {
            save();
        } catch (IOException error) {
            points -= refund;
            redeemed[index]++;
            throw error;
        }
        return refund;
    }

    private enum DefinitionRef {
        FIRST_PLAY("first_play"), PIPES_10("pipes_10"), PIPES_25("pipes_25"),
        POKER_WIN("poker_win"), STREAK_3("streak_3"), REDEMPTIONS_5("redemptions_5");
        final Achievements.Definition definition;
        DefinitionRef(String id) {
            for (Achievements.Definition candidate : Achievements.ALL) {
                if (candidate.id().equals(id)) { definition = candidate; return; }
            }
            definition = null;
        }
    }

    /** Records a newly unlocked achievement in {@code fired}; returns nothing to the caller. */
    private int grant(DefinitionRef ref, List<AchievementEvent> fired) {
        Achievements.Definition definition = ref.definition;
        if (definition == null || unlocked.contains(definition.id())) return 0;
        unlocked.add(definition.id());
        fired.add(new AchievementEvent(definition, definition.grant()));
        return definition.grant();
    }

    private void fire(List<AchievementEvent> events) {
        if (onAchievement == null) return;
        for (AchievementEvent event : events) onAchievement.accept(event);
    }

    private void load() throws IOException {
        if (!Files.exists(file)) return;
        loadedExisting = true;
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) { properties.load(in); }
        int version = integer(properties.getProperty("version"), 1);
        if (version > FILE_VERSION) throw new IOException("Progress file is from a newer app: " + file);
        try {
            points = integer(properties.getProperty("points"), EconomyRules.STARTING_POINTS);
            earnedToday = integer(properties.getProperty("earnedToday"), 0);
            earnedDate = LocalDate.parse(properties.getProperty("earnedDate", today.toString()));
            for (int i = 0; i < redeemed.length; i++) {
                redeemed[i] = integer(properties.getProperty("redeemed." + i), 0);
            }
            if (version >= FILE_VERSION) {
                streakDays = integer(properties.getProperty("streakDays"), 0);
                lastBonusDate = properties.getProperty("lastBonusDate") == null
                        ? null : LocalDate.parse(properties.getProperty("lastBonusDate"));
                bestRun = integer(properties.getProperty("bestRun"), 0);
                gamesPlayed = integer(properties.getProperty("gamesPlayed"), 0);
                pokerWins = integer(properties.getProperty("pokerWins"), 0);
                doubledToday = "true".equals(properties.getProperty("doubledToday", "false"));
                for (String id : properties.getProperty("unlockedAchievements", "").split(",")) {
                    if (!id.isBlank()) unlocked.add(id);
                }
            }
        } catch (RuntimeException error) {
            throw new IOException("Progress file is invalid: " + file, error);
        }
        if (!earnedDate.equals(today)) {
            earnedDate = today;
            earnedToday = 0;
        }
        // The per-pipe cap is enforced at write time, but today's achievements
        // (at most ~1,600) add to earnedToday legally, so allow slack for those.
        if (earnedToday > EconomyRules.DAILY_EARNINGS_CAP + 2_000) {
            throw new IOException("Progress file exceeds daily cap: " + file);
        }
    }

    private static int integer(String text, int fallback) {
        int value = text == null ? fallback : Integer.parseInt(text);
        if (value < 0) throw new IllegalArgumentException("negative saved value: " + value);
        return value;
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        Properties properties = new Properties();
        properties.setProperty("version", Integer.toString(FILE_VERSION));
        properties.setProperty("points", Integer.toString(points));
        properties.setProperty("earnedToday", Integer.toString(earnedToday));
        properties.setProperty("earnedDate", earnedDate.toString());
        for (int i = 0; i < redeemed.length; i++) {
            properties.setProperty("redeemed." + i, Integer.toString(redeemed[i]));
        }
        properties.setProperty("streakDays", Integer.toString(streakDays));
        if (lastBonusDate != null) properties.setProperty("lastBonusDate", lastBonusDate.toString());
        properties.setProperty("bestRun", Integer.toString(bestRun));
        properties.setProperty("gamesPlayed", Integer.toString(gamesPlayed));
        properties.setProperty("pokerWins", Integer.toString(pokerWins));
        properties.setProperty("doubledToday", Boolean.toString(doubledToday));
        properties.setProperty("unlockedAchievements", String.join(",", unlocked));
        Path temp = Files.createTempFile(file.getParent(), "progress-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                properties.store(out, "Rewards JavaFX progress");
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        if (onBalanceChanged != null) onBalanceChanged.accept(points);
    }
}
