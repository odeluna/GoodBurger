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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import rewards.ProgressStore;

import java.io.IOException;

/**
 * Controller for checkout_page.fxml.
 *
 * <p>The shared {@link CartView} (the same cart panel the menu page uses, with - and + on every row) is
 * dropped into {@code cartSlot}. The subtotal, tax and total labels and the pickup-time choice live on this
 * page; "Submit" calls {@link OrderManager#checkout}, which saves the order to orders.csv and empties the
 * cart. Page navigation is the shared {@code NavBar} in the FXML.</p>
 *
 * <p>The rewards block under the total shows the signed-in user's point balance and every item they
 * redeemed on the rewards page, with a "Trade in" button that returns half the item's cost in points
 * (the same {@link UserRewardStore} the rewards page uses, so both screens always agree).</p>
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
        }
        renderRewards();
    }

    private void refreshTotals() {
        subtotal.setText(String.format("$%.2f", orderManager.getSubtotal()));
        tax.setText(String.format("$%.2f", orderManager.getTax()));
        total.setText(String.format("$%.2f", orderManager.getTotal()));
    }

    // Lists the point balance and every redeemed reward with a trade-in button.
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
            Label item = new Label(name + " x" + count);
            item.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #213542;");
            Button trade = new Button("Trade in");
            trade.setTooltip(new Tooltip("Return the " + name.toLowerCase()
                    + " for +" + Math.round(progress.tradeBackValue(i)) + " pts (half its cost)"));
            trade.setStyle("-fx-font-size: 11.5px; -fx-padding: 3 10 3 10; -fx-background-radius: 6;");
            final int itemIndex = i;
            trade.setOnAction(event -> tradeIn(itemIndex));
            HBox row = new HBox(8, item, trade);
            row.setAlignment(Pos.CENTER_LEFT);
            rewardsItems.getChildren().add(row);
        }
        if (!any) {
            Label none = new Label("Nothing redeemed yet");
            none.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #5a6b74;");
            rewardsItems.getChildren().add(none);
        }
    }

    // Trades a redeemed item back in for half its cost in points.
    private void tradeIn(int index) {
        try {
            int refund = progress.tradeIn(index);
            renderRewards();
            new Alert(Alert.AlertType.INFORMATION,
                    "Traded in the " + ProgressStore.REWARD_NAMES[index].toLowerCase()
                            + ": +" + refund + " pts added to your rewards balance.").show();
        } catch (IOException error) {
            new Alert(Alert.AlertType.ERROR,
                    "Could not save the trade-in: " + error.getMessage()).show();
        }
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
            String orderId = orderManager.checkout(session.getCurrentUser(), type, pickup);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Order Submitted");
            alert.setHeaderText("Your order has been submitted!");
            alert.setContentText("Order " + orderId + ", pickup: " + pickup);
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
