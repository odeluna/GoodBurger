package edu.utsa.cs3443.goodburger.controller;

import edu.utsa.cs3443.goodburger.GoodBurgerApplication;
import edu.utsa.cs3443.goodburger.model.Nutrition;
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
import javafx.scene.layout.StackPane;

import java.io.IOException;

/**
 * Controller for checkout_page.fxml.
 *
 * <p>The shared {@link CartView} (the same cart panel the menu page uses, with - and + on every row) is
 * dropped into {@code cartSlot}. The subtotal, tax and total labels and the pickup-time choice live on this
 * page; "Submit" calls {@link OrderManager#checkout}, which saves the order to orders.csv and empties the
 * cart. Page navigation is the shared {@code NavBar} in the FXML.</p>
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
    @FXML private Label nutritionLabel;

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
    }

    private void refreshTotals() {
        subtotal.setText(String.format("$%.2f", orderManager.getSubtotal()));
        tax.setText(String.format("$%.2f", orderManager.getTax()));
        total.setText(String.format("$%.2f", orderManager.getTotal()));

        // Same format as the item detail pane: Nutrition.toString(), here summed over the whole order.
        Nutrition sum = Nutrition.ZERO;
        for (OrderItem line : orderManager.getItems()) {
            sum = sum.add(line.getNutrition());
        }
        nutritionLabel.setText(sum.toString());
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
