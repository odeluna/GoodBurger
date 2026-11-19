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

/** Saves rewards, daily bonuses, and poker wins. Existing progress files are supported. */
public final class ProgressStore {
    public record AchievementEvent(Achievements.Definition definition, int grant) { }

    public static final String[] REWARD_NAMES = {"Soda", "Fries", "Salad"};
    public static final int[] REWARD_COSTS = {300, 500, 1000};
    static final int TRADE_BACK_PERCENT = 50;
    private static final int FILE_VERSION = 2;

    private final Path file;
    private final Clock clock;
    private int points = EconomyRules.STARTING_POINTS;
    private final int[] redeemed = new int[REWARD_NAMES.length];
    private int streakDays;
    private LocalDate lastBonusDate;
    private int pokerWins;
    private final List<String> unlocked = new ArrayList<>();
    private Consumer<AchievementEvent> onAchievement;

    public ProgressStore(Path file, Clock clock) throws IOException {
        this.file = file;
        this.clock = clock;
        load();
    }

    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".local", "share",
                "rewards-javafx", "progress.properties");
    }

    public synchronized int points() { return points; }
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
        LocalDate day = liveDay();
        int streak = day.equals(lastBonusDate) || day.minusDays(1).equals(lastBonusDate) ? streakDays : 0;
        return Math.min(EconomyRules.DAILY_BONUS_MAX,
                EconomyRules.DAILY_BONUS_BASE + streak * EconomyRules.DAILY_BONUS_PER_STREAK_DAY);
    }
    public synchronized int pokerWins() { return pokerWins; }
    /** True when today's per-pipe earnings already reach the daily cap. */
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
    /**
     * Daily login bonus: +100 base, +25 per consecutive day, capped at 300.
     * Skipped when the day was already claimed; the streak resets on a missed day.
     */
    public synchronized int claimDailyBonus() throws IOException {
        LocalDate live = liveDay();
        if (lastBonusDate != null && lastBonusDate.equals(live)) return 0;
        int oldStreak = streakDays;
        LocalDate oldDate = lastBonusDate;
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
        try {
            save();
        } catch (IOException error) {
            points -= bonus + achievements;
            streakDays = oldStreak;
            lastBonusDate = oldDate;
            if (!fired.isEmpty()) unlocked.remove(DefinitionRef.STREAK_3.definition.id());
            throw error;
        }
        fire(fired);
        return bonus;
    }

    /** Saves each win. The first win also awards the Card Shark achievement. */
    public synchronized void recordPokerWin() throws IOException {
        pokerWins++;
        List<AchievementEvent> fired = new ArrayList<>();
        int grant = grant(DefinitionRef.POKER_WIN, fired);
        points += grant;
        try {
            save();
        } catch (IOException error) {
            points -= grant;
            pokerWins--;
            if (!fired.isEmpty()) unlocked.remove(DefinitionRef.POKER_WIN.definition.id());
            throw error;
        }
        fire(fired);
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
        points += grantTotal;
        try {
            save();
        } catch (IOException error) {
            points += REWARD_COSTS[index] - grantTotal;
            redeemed[index]--;
            if (!fired.isEmpty()) unlocked.remove(DefinitionRef.REDEMPTIONS_5.definition.id());
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
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) { properties.load(in); }
        int version = integer(properties.getProperty("version"), 1);
        if (version > FILE_VERSION) throw new IOException("Progress file is from a newer app: " + file);
        try {
            points = integer(properties.getProperty("points"), EconomyRules.STARTING_POINTS);
            for (int i = 0; i < redeemed.length; i++) {
                redeemed[i] = integer(properties.getProperty("redeemed." + i), 0);
            }
            if (version >= FILE_VERSION) {
                streakDays = integer(properties.getProperty("streakDays"), 0);
                lastBonusDate = properties.getProperty("lastBonusDate") == null
                        ? null : LocalDate.parse(properties.getProperty("lastBonusDate"));
                pokerWins = integer(properties.getProperty("pokerWins"), 0);
                for (String id : properties.getProperty("unlockedAchievements", "").split(",")) {
                    for (Achievements.Definition achievement : Achievements.ALL) {
                        if (achievement.id().equals(id)) unlocked.add(id);
                    }
                }
            }
        } catch (RuntimeException error) {
            throw new IOException("Progress file is invalid: " + file, error);
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
    }
}
