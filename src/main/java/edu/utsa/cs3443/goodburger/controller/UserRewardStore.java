package edu.utsa.cs3443.goodburger.controller;

import edu.utsa.cs3443.goodburger.model.AccountManager;
import edu.utsa.cs3443.goodburger.model.MenuManager;
import edu.utsa.cs3443.goodburger.model.MenuItem;
import edu.utsa.cs3443.goodburger.model.OrderItem;
import edu.utsa.cs3443.goodburger.model.OrderManager;
import edu.utsa.cs3443.goodburger.model.Session;
import edu.utsa.cs3443.goodburger.model.User;

import rewards.ProgressStore;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Shared access to the signed-in user's rewards store.
 *
 * <p>Both the rewards page and the checkout open the same per-account
 * {@link ProgressStore} through this helper, so the point balance and the
 * redeemed items agree on every screen. The signed-in user's row in
 * data/users.csv is the source of truth for the balance: the store starts at
 * that balance, and every change (a poker result, a redemption, a trade-in at
 * checkout) is written back to the row. Guests use the shared default
 * progress file and never touch users.csv.
 */
public final class UserRewardStore {

    /**
     * The menu item behind each reward, in the same order as
     * {@link ProgressStore#REWARD_NAMES}: Soda is the Soft Drink (D01), Fries are the
     * French Fries (S02), Salad is the Side Salad (S06). Redeeming one puts that menu
     * item into the order as a free line.
     */
    private static final String[] REWARD_MENU_IDS = {"D01", "S02", "S06"};

    private UserRewardStore() { }

    /** @return the menu id behind the reward at this index (same order as REWARD_NAMES) */
    public static String rewardMenuItemId(int index) {
        if (index < 0 || index >= REWARD_MENU_IDS.length) {
            throw new IndexOutOfBoundsException(index);
        }
        return REWARD_MENU_IDS[index];
    }

    /**
     * Keeps the shared order in step with the rewards store: after this call the cart holds
     * exactly {@code count} free lines for the reward's menu item. Adds a line, grows or shrinks
     * the existing one, or removes it. This is what makes a redeemed reward show up under the
     * current order at checkout, the same way a menu item does.
     *
     * <p>Only lines marked {@link OrderItem#isRedeemed()} are touched, so a French Fries the
     * customer bought from the menu and a French Fries they redeemed never mix up.</p>
     */
    public static void syncRewardToCart(OrderManager orders, MenuManager menu, int index, int count) {
        if (orders == null || menu == null) return;
        MenuItem menuItem = menu.findById(rewardMenuItemId(index));
        if (menuItem == null) return;
        OrderItem line = null;
        for (OrderItem candidate : orders.getItems()) {
            if (candidate.isRedeemed() && candidate.getItem().getId().equals(menuItem.getId())) {
                line = candidate;
                break;
            }
        }
        if (count <= 0) {
            if (line != null) orders.changeQuantity(line, -line.getQuantity());
            return;
        }
        if (line == null) {
            OrderItem fresh = new OrderItem(menuItem);
            fresh.setQuantity(count);
            fresh.setRedeemed(true);
            orders.addItem(fresh);
        } else if (line.getQuantity() != count) {
            orders.changeQuantity(line, count - line.getQuantity());
        }
    }

    /**
     * Opens the per-account store for the current session user. Falls back to
     * a throwaway file if the main path cannot be read, so the pages stay
     * usable; returns null only if even the fallback fails.
     */
    public static ProgressStore open() {
        User user = Session.get().getCurrentUser();
        String path = System.getProperty("rewards.progress.path");
        try {
            return new ProgressStore(resolveStorePath(user, path), Clock.systemDefaultZone());
        } catch (IOException error) {
            try {
                return new ProgressStore(
                        java.nio.file.Files.createTempDirectory("rewards-page").resolve("progress.properties"),
                        Clock.systemDefaultZone());
            } catch (IOException fallback) {
                return null;
            }
        }
    }

    /**
     * Ties the balance to the signed-in account: starts at the points from
     * users.csv and mirrors every change back to that user's row. Guests use
     * the store file as-is. Returns false when the user row could not be read.
     */
    public static boolean attachToUser(ProgressStore store) {
        User user = Session.get().getCurrentUser();
        if (user == null || user.isGuest()) return true;
        try {
            store.setBalance(user.getPoints());
            store.onBalanceChanged(points -> {
                if (!sync(points)) {
                    javafx.scene.control.Alert alert =
                            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Could not save your points");
                    alert.setHeaderText(null);
                    alert.setContentText("users.csv write failed");
                    alert.show();
                }
            });
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    /**
     * Writes the rewards balance back to the signed-in user's row in
     * users.csv. No-op for guests and when the row already holds this value.
     */
    public static boolean sync(int points) {
        User user = Session.get().getCurrentUser();
        if (user == null || user.isGuest() || user.getPoints() == points) return true;
        user.setPoints(points);
        try {
            AccountManager manager = Session.get().getAccountManager();
            List<User> users = manager.loadUserFromFile();
            for (User stored : users) {
                if (stored.getEmail().equalsIgnoreCase(user.getEmail())) {
                    stored.setPoints(points);
                    return manager.saveUserToFile(users);
                }
            }
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    /**
     * One progress file per account, next to the default file and named after
     * the user id, so each signed-in user keeps their own rewards state. The
     * rewards.progress.path property still wins, and guests keep the default.
     */
    public static Path resolveStorePath(User user, String propertyOverride) {
        if (propertyOverride != null) return Path.of(propertyOverride);
        if (user != null && !user.isGuest() && !user.getId().isBlank()) {
            return ProgressStore.defaultPath()
                    .resolveSibling("progress-" + user.getId() + ".properties");
        }
        return ProgressStore.defaultPath();
    }
}
