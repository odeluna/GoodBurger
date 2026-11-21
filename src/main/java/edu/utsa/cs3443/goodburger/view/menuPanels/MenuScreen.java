package edu.utsa.cs3443.goodburger.view.menuPanels;

import edu.utsa.cs3443.goodburger.controller.UserRewardStore;
import edu.utsa.cs3443.goodburger.model.Category;
import edu.utsa.cs3443.goodburger.model.MenuItem;
import edu.utsa.cs3443.goodburger.model.MenuManager;
import edu.utsa.cs3443.goodburger.model.Modifier;
import edu.utsa.cs3443.goodburger.model.OrderItem;
import edu.utsa.cs3443.goodburger.model.OrderManager;
import edu.utsa.cs3443.goodburger.view.CartView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import java.util.Objects;

/**
 * The food menu screen: one screen with three areas whose contents swap as the customer clicks.
 *
 * <pre>
 *  +-------------+-------------------------------+-----------------------+
 *  | left        | middle                        | right                 |
 *  | Category    | ItemGridPanel                 | CartView          |
 *  | Panel       |   or ItemDetailPanel          |   or ItemPreviewPanel |
 *  +-------------+-------------------------------+-----------------------+
 * </pre>
 *
 * <p><b>Why one screen instead of several?</b> Your design keeps the category column and the cart
 * visible the whole time, so only parts of the screen change. Each changing area is a
 * {@link StackPane} holding both of its views on top of each other; showing one and hiding the other
 * swaps them instantly without reloading anything.</p>
 *
 * <p><b>Who decides what:</b> the panels only draw and report clicks. This class holds the state
 * and decides what each click does. The rule is simple:</p>
 * <ul>
 *   <li>{@code draftItem == null}: the customer is browsing, so show the item grid and the cart;</li>
 *   <li>{@code draftItem != null}: they are customizing an item, so show its details and its preview.</li>
 * </ul>
 * <p>Every click handler changes the state, then calls {@link #refreshPanels()} to apply that rule.</p>
 *
 * <p><b>Why extend BorderPane?</b> BorderPane already has left, center and right slots, which match the
 * Figma layout exactly. The bottom slot is left free for the app's navigation bar (home, menu, rewards,
 * checkout), which belongs to the whole app rather than the food menu.</p>
 */
public class MenuScreen extends BorderPane {

    // ---- State ----
    private final MenuManager menu;       // shared, read-only menu loaded at startup
    private final OrderManager orders;    // the shared order; the cart on every screen shows it
    private Category selectedCategory;    // which category's items the grid is showing
    private OrderItem draftItem;          // item being customized, or null while browsing

    // ---- Panels ----
    // Each is created once and reused; "show(...)" refills it. Handlers are passed as method references.
    private final CategoryPanel categoryPanel = new CategoryPanel(this::onCategoryClicked);
    private final ItemGridPanel itemGridPanel = new ItemGridPanel(this::onItemClicked);
    private final ItemDetailPanel itemDetailPanel = new ItemDetailPanel(this::onModifierToggled, this::onQuantityChanged);
    private final CartView cartPanel;
    private final ItemPreviewPanel itemPreviewPanel = new ItemPreviewPanel(this::onAddClicked, this::onCancelClicked);

    /**
     * Builds the screen and opens it on the first category with an empty draft.
     *
     * @param menu   the loaded menu (see {@link edu.utsa.cs3443.goodburger.model.DataManager#loadMenu})
     * @param orders the shared order that "Add" puts items into
     */
    public MenuScreen(MenuManager menu, OrderManager orders) {
        this.menu = menu;
        this.orders = orders;
        getStyleClass().add("menu-screen");   // see styles/menuScreen.css; every panel below inherits it
        getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/styles/menuScreen.css")).toExternalForm());
        this.cartPanel = new CartView(orders, "Item Cart", true);
        cartPanel.setPrefWidth(280);
        // Rewards lines behave the same here as at checkout: - takes the unit back (its full
        // price returns to the points), + adds a unit at the full redemption price.
        cartPanel.setOnRedeemedMinus(line -> UserRewardStore.refundRedemption(freshRewardStore(), line));
        cartPanel.setOnRedeemedPlus(line -> UserRewardStore.chargeRedemption(freshRewardStore(), line));

        setLeft(categoryPanel);
        setCenter(new StackPane(itemGridPanel, itemDetailPanel));
        setRight(new StackPane(cartPanel, itemPreviewPanel));

        categoryPanel.show(Category.values());
        onCategoryClicked(Category.values()[0]);   // start on Entrees so the grid isn't empty
    }

    /**
     * A fresh rewards store for the signed-in user, opened per interaction so the menu screen
     * never holds a stale balance or redemption count (redeemptions can happen on the rewards
     * page while this screen is alive). @return null when no store can be opened, which leaves
     * the cart line changes uncharged.
     */
    private rewards.ProgressStore freshRewardStore() {
        rewards.ProgressStore store = UserRewardStore.open();
        if (store != null) {
            UserRewardStore.attachToUser(store);
        }
        return store;
    }

    // ---- Click handlers: change state, then refresh ----

    /** A category button was pressed: show that category's items and drop any half-finished item. */
    public void onCategoryClicked(Category c) {
        selectedCategory = c;
        categoryPanel.setSelected(c);   // fuchsia highlight on the chosen category
        draftItem = null;   // switching category acts like Cancel, matching the "left column is a back button" design
        itemGridPanel.show(menu.getByCategory(c));
        refreshPanels();
    }

    /** An item button was pressed: start a new draft for it and show its details and preview. */
    public void onItemClicked(MenuItem item) {
        draftItem = new OrderItem(item);
        itemDetailPanel.show(item);
        itemPreviewPanel.show(draftItem);
        refreshPanels();
    }

    /** An add-on checkbox changed: update the draft and redraw the preview so the price follows it. */
    public void onModifierToggled(Modifier m) {
        if (draftItem == null) return;   // defensive: checkboxes are only visible while a draft exists
        draftItem.toggleModifier(m);
        itemPreviewPanel.show(draftItem);
    }

    /** The quantity spinner changed: update the draft and redraw the preview. */
    public void onQuantityChanged(int q) {
        if (draftItem == null) return;   // the spinner also fires while being reset between items
        draftItem.setQuantity(q);
        itemPreviewPanel.show(draftItem);
    }

    /** "Add" was pressed: move the draft into the order and go back to browsing. */
    public void onAddClicked() {
        if (draftItem == null) return;
        orders.addItem(draftItem);
        draftItem = null;
        refreshPanels();
    }

    /** "Cancel" was pressed: throw the draft away and go back to browsing. Nothing in the order changes. */
    public void onCancelClicked() {
        draftItem = null;
        refreshPanels();
    }

    /**
     * Shows the right view in the middle and right areas, based only on whether a draft exists.
     * Keeping this in one method means no handler can leave the screen half-switched.
     */
    private void refreshPanels() {
        boolean customizing = draftItem != null;
        itemGridPanel.setVisible(!customizing);
        cartPanel.setVisible(!customizing);
        itemDetailPanel.setVisible(customizing);
        itemPreviewPanel.setVisible(customizing);
    }

    /** @return the category currently shown in the grid */
    public Category getSelectedCategory() {
        return selectedCategory;
    }
}
