package edu.utsa.cs3443.goodburger.controller;

import edu.utsa.cs3443.goodburger.GoodBurgerApplication;
import edu.utsa.cs3443.goodburger.model.OrderItem;
import edu.utsa.cs3443.goodburger.model.OrderManager;
import edu.utsa.cs3443.goodburger.model.OrderType;
import edu.utsa.cs3443.goodburger.model.Session;
import edu.utsa.cs3443.goodburger.view.CartView;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import rewards.ProgressStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for checkout_page.fxml.
 *
 * <p>The shared {@link CartView} (the same cart panel the menu page uses, with - and + on every row) is
 * dropped into {@code cartSlot}. The subtotal, tax and total labels and the pickup-time choice live on this
 * page; "Submit" calls {@link OrderManager#checkout}, which saves the order to orders.csv and empties the
 * cart. Page navigation is the shared {@code NavBar} in the FXML.</p>
 *
 * <p>Redeemed rewards (soda, fries, salad) land in the shared order the same way a menu item
 * does: a free line that shows under the current order here and on the menu page. Opening this
 * page syncs the order from the per-user rewards store, so the lines agree no matter which
 * screen the redemption happened on. The rewards block under the total shows the point balance
 * and what has been redeemed. Pressing - on a redeemed line takes the unit back and gives its
 * FULL redemption price straight back to the balance; pressing + adds another unit at its full
 * price. When the order is submitted the redeemed lines stay in the saved order and earn no
 * points, while the paid lines earn 10 points per dollar (the same {@link UserRewardStore} the
 * rewards page uses, so all screens always agree). The submitted units are consumed, so the
 * redeemed items are not in the order list after the submit.</p>
 */
public class CheckoutScreenController {

    private static final String ASAP = "ASAP";

    private final Session session = Session.get();
    private final OrderManager orderManager = session.getOrderManager();

    // Kept in a field so the weak listener below isn't garbage collected while this screen is open.
    private final ListChangeListener<OrderItem> itemsListener = change -> refreshTotals();

    @FXML private StackPane cartSlot;
    @FXML private ChoiceBox<String> pickup_time;
    @FXML private Label subtotal;
    @FXML private Label tax;
    @FXML private Label total;
    @FXML private Label rewardsPoints;
    @FXML private VBox rewardsItems;

    private ProgressStore progress;

    @FXML
    void initialize() {
        CartView cart = new CartView(orderManager, null, false);   // this page shows its own totals
        cart.setMaxHeight(Double.MAX_VALUE);
        cartSlot.getChildren().add(cart);

        // The cart's - on a redeemed line gives the full redemption price back; + charges it.
        cart.setOnRedeemedMinus(line -> {
            if (!UserRewardStore.refundRedemption(progress, line)) {
                new Alert(Alert.AlertType.ERROR, "Could not save your points. The item stayed in your order.").show();
                return false;
            }
            renderRewards();
            return true;
        });
        cart.setOnRedeemedPlus(line -> {
            if (!UserRewardStore.chargeRedemption(progress, line)) {
                new Alert(Alert.AlertType.INFORMATION, "Not enough points for another "
                        + line.getItem().getName().toLowerCase() + ".").show();
                return false;
            }
            renderRewards();
            return true;
        });

        pickup_time.getItems().addAll(ASAP, "In 15 minutes", "In 30 minutes", "In 45 minutes", "In 1 hour");
        pickup_time.setValue(ASAP);

        subtotal.setVisible(true);
        tax.setVisible(true);
        total.setVisible(true);
        // Weak: the shared cart outlives this screen, so a strong listener would keep every old checkout page alive.
        orderManager.getItems().addListener(new WeakListChangeListener<>(itemsListener));
        refreshTotals();

        // Same per-user rewards store as the rewards page, so redeemed items
        // and the point balance agree on both screens.
        progress = UserRewardStore.open();
        if (progress != null) {
            UserRewardStore.attachToUser(progress);
            // Every redeemed reward shows under the current order as a free line,
            // the same way a menu item does.
            for (int i = 0; i < ProgressStore.REWARD_NAMES.length; i++) {
                UserRewardStore.syncRewardToCart(orderManager, session.getMenuManager(),
                        i, progress.redeemedCount(i));
            }
        }
        renderRewards();
    }

    private void refreshTotals() {
        subtotal.setText(String.format("$%.2f", orderManager.getSubtotal()));
        tax.setText(String.format("$%.2f", orderManager.getTax()));
        total.setText(String.format("$%.2f", orderManager.getTotal()));
    }

    // Lists the point balance and every redeemed reward. The lines are informational: the
    // customer gets their points back by pressing - on the line in the cart (full price back),
    // and the items stay in the saved order when the order is submitted.
    private void renderRewards() {
        if (progress == null) {
            rewardsPoints.setText("Points: --");
            rewardsItems.getChildren().add(new Label("Rewards unavailable"));
            return;
        }
        rewardsPoints.setText("Points: " + progress.points());
        rewardsItems.getChildren().clear();
        boolean any = false;
        for (int i = 0; i < ProgressStore.REWARD_NAMES.length; i++) {
            int count = progress.redeemedCount(i);
            if (count == 0) continue;
            any = true;
            String name = ProgressStore.REWARD_NAMES[i];
            Label item = new Label(name + " x" + count + "  (press \u2212 in the cart to take it back)");
            item.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #213542;");
            item.setTooltip(new Tooltip("Press \u2212 on the " + name.toLowerCase() + " line in your order to remove it: "
                    + "+" + ProgressStore.REWARD_COSTS[i] + " pts back (its full cost)"));
            rewardsItems.getChildren().add(item);
        }
        if (!any) {
            Label none = new Label("Nothing redeemed yet");
            none.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #5a6b74;");
            rewardsItems.getChildren().add(none);
        }
    }

    /**
     * The points this order earns when it is submitted: 10 per dollar of item price,
     * counted over the paid lines only. Redeemed lines cost $0.00, so they earn nothing.
     */
    private int orderEarnsPoints() {
        int earned = 0;
        for (OrderItem line : orderManager.getItems()) {
            if (line.isRedeemed()) continue;
            earned += Math.round(line.getLinePrice() * 10);
        }
        return earned;
    }

    @FXML
    void pay_now_clicked(ActionEvent event) {
        if (orderManager.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Your cart is empty. Add something from the menu first.").show();
            return;
        }
        String pickup = pickup_time.getValue() == null ? ASAP : pickup_time.getValue();
        OrderType type = ASAP.equals(pickup) ? OrderType.ASAP : OrderType.SCHEDULED;
        try {
            // Redeemed items stay in the saved order (they earn no points); the paid lines
            // earn 10 points per dollar of item price.
            int earned = orderEarnsPoints();
            // Redeemed lines are saved as-is; their pending units are consumed after the order
            // is saved, so they do not reappear in the order the next time this page (or the
            // rewards page) opens.
            List<OrderItem> redeemedLines = new ArrayList<>();
            for (OrderItem line : orderManager.getItems()) {
                if (line.isRedeemed()) redeemedLines.add(line);
            }
            String orderId = orderManager.checkout(session.getCurrentUser(), type, pickup);
            if (progress != null) {
                for (OrderItem line : redeemedLines) {
                    if (!UserRewardStore.consumeRedemption(progress, line)) {
                        new Alert(Alert.AlertType.ERROR, "Could not save your points.").show();
                        break;
                    }
                }
                if (earned > 0) {
                    progress.adjustPoints(earned);
                }
            }
            String rewardNote = earned > 0
                    ? " You earned " + earned + " pts (10 per $1 of item price)."
                    : " Items you redeemed with points don't earn points.";
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Order Submitted");
            alert.setHeaderText("Your order has been submitted!");
            alert.setContentText("Order " + orderId + ", pickup: " + pickup + rewardNote);
            alert.showAndWait();
            goTo(event, "home_page.fxml");
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not save the order: " + e.getMessage()).show();
        }
    }

    // Switches to another screen
    private void goTo(ActionEvent event, String fxmlFile) {
        try {
            GoodBurgerApplication.switchScreen((Node) event.getSource(), fxmlFile);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open " + fxmlFile).show();
        }
    }
}
