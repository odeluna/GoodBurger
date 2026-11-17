package edu.utsa.cs3443.goodburger.view.menuPanels;

import edu.utsa.cs3443.goodburger.controller.MenuScreenController;
import edu.utsa.cs3443.goodburger.model.Order;
import edu.utsa.cs3443.goodburger.model.OrderItem;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Right area, first view: everything in the current {@link Order} and the running total.
 *
 * <p>It only reads the order; adding items is done by {@link MenuScreenController}, which then calls {@link #show}
 * so this view always matches the order. See {@link CategoryPanel} for why panels work this way.</p>
 */
public class CartPanel extends VBox {

    /**
     * Sales tax rate used for the total shown here: 8.25%, the San Antonio rate. Checkout owns the real
     * calculation; this is only so the menu can show a total like the Figma design does.
     */
    static final double TAX_RATE = 0.0825;

    private final ListView<OrderItem> itemList = new ListView<>();
    private final Label subtotalLabel = new Label();
    private final Label taxLabel = new Label();
    private final Label totalLabel = new Label();

    public CartPanel() {
        super(8);
        setPadding(new Insets(10));
        setPrefWidth(260);
        Label title = new Label("Item Cart");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        totalLabel.setStyle("-fx-font-weight: bold;");
        itemList.setPlaceholder(new Label("Your cart is empty."));
        VBox.setVgrow(itemList, Priority.ALWAYS);   // the list takes all spare height; totals stay at the bottom
        getChildren().addAll(title, itemList, subtotalLabel, taxLabel, totalLabel);
    }

    /**
     * Redraws the list and totals from the order.
     *
     * @param order the customer's current order
     */
    public void show(Order order) {
        itemList.getItems().setAll(order.getItems());   // each row uses OrderItem.toString()
        double subtotal = 0;
        for (OrderItem item : order.getItems()) {
            subtotal += item.getLinePrice();
        }
        double tax = subtotal * TAX_RATE;
        subtotalLabel.setText(String.format("Subtotal: $%.2f", subtotal));
        taxLabel.setText(String.format("Tax: $%.2f", tax));
        totalLabel.setText(String.format("Total: $%.2f", subtotal + tax));
    }
}
