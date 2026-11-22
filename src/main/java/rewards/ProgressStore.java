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

/**
 * Persists the balance, redemptions, login streak, achievements, and the
 * local-calendar-day earnings cap. Legacy v1 files (balance/redemptions only) load
 * unchanged; new fields default to empty and the file is rewritten as v2 on save.
 */
public final class ProgressStore {
    /** Fired on the FX thread so the UI can show one-shot reward toasts. */
    public record AchievementEvent(Achievements.Definition definition, int grant) { }

    public static final String[] REWARD_NAMES = {"Soda", "Fries", "Salad"};
    public static final int[] REWARD_COSTS = {300, 500, 1000};
    static final int TRADE_BACK_PERCENT = 50;
    private static final int FILE_VERSION = 2;

    private final Path file;
    private final Clock clock;
    private final LocalDate today;
    private boolean loadedExisting;
    private int points;
    private int earnedToday;
    private LocalDate earnedDate;
    private final int[] redeemed = new int[REWARD_NAMES.length];
    private int streakDays;
    private LocalDate lastBonusDate;
    private int pokerWins;
    private final List<String> unlocked = new ArrayList<>();
    private Consumer<AchievementEvent> onAchievement;
    private Consumer<Integer> onBalanceChanged;

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
    public synchronized int pokerWins() { return pokerWins; }
    /** True when the login bonus for the current local day has already been claimed. */
    public synchronized boolean bonusClaimedToday() {
        return lastBonusDate != null && lastBonusDate.equals(liveDay());
    }
    public synchronized double tradeBackValue(int index) {
        return REWARD_COSTS[index] * TRADE_BACK_PERCENT / 100.0;
    }

    /** UI hook: show achievement toasts on the FX thread. */
    public synchronized void onAchievementAwarded(Consumer<AchievementEvent> consumer) {
        onAchievement = consumer;
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

    /**
     * Consumes a redeemed unit that was just submitted in an order: the count
     * drops by one but no points move (the customer already paid at redemption).
     * Call it after the order is saved, so a failed save leaves the unit pending.
     */
    public synchronized void consumeRedemption(int index) throws IOException {
        if (index < 0 || index >= REWARD_NAMES.length) throw new IndexOutOfBoundsException(index);
        if (redeemed[index] == 0) return;
        redeemed[index]--;
        try {
            save();
        } catch (IOException error) {
            redeemed[index]++;
            throw error;
        }
    }

    private enum DefinitionRef {
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
            points = integer(properties.getProperty("points"), 0);
            earnedToday = integer(properties.getProperty("earnedToday"), 0);
            earnedDate = LocalDate.parse(properties.getProperty("earnedDate", today.toString()));
            for (int i = 0; i < redeemed.length; i++) {
                redeemed[i] = integer(properties.getProperty("redeemed." + i), 0);
            }
            if (version >= FILE_VERSION) {
                streakDays = integer(properties.getProperty("streakDays"), 0);
                lastBonusDate = properties.getProperty("lastBonusDate") == null
                        ? null : LocalDate.parse(properties.getProperty("lastBonusDate"));
                pokerWins = integer(properties.getProperty("pokerWins"), 0);
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
        properties.setProperty("pokerWins", Integer.toString(pokerWins));
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
