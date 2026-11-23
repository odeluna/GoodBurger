package edu.utsa.cs3443.goodburger.view;

import edu.utsa.cs3443.goodburger.model.OrderManager;
import edu.utsa.cs3443.goodburger.model.OrderItem;
import edu.utsa.cs3443.goodburger.model.Modifier;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The shopping cart, shared by every screen that shows it (menu page and checkout page).
 *
 * <p>It shows the {@link OrderManager}'s items directly, so it always matches the real order.
 * Each row has - and + buttons; pressing - on a quantity of 1 removes the row.</p>
 */
public class CartView extends VBox {

    private final OrderManager orderManager;
    private final ListView<OrderItem> itemList;
    private final Label subtotalLabel = new Label();
    private final Label taxLabel = new Label();
    private final Label totalLabel = new Label();

    /**
     * Pressed on the - of a rewards (redeemed) line before the unit is removed. The owner of the
     * cart wires this to the rewards store so the unit's full redemption price comes back to the
     * balance. @return false keeps the line in the order (the points could not be saved).
     */
    private Predicate<OrderItem> onRedeemedMinus = item -> true;

    /**
     * Pressed on the + of a rewards (redeemed) line before the unit is added. The owner of the
     * cart wires this to the rewards store so the extra unit costs its full redemption price.
     * @return false when the customer cannot afford it, so no unit is added.
     */
    private Predicate<OrderItem> onRedeemedPlus = item -> true;

    /** Wires the - button of redeemed lines to a full-points refund. */
    public void setOnRedeemedMinus(Predicate<OrderItem> handler) { this.onRedeemedMinus = handler; }

    /** Wires the + button of redeemed lines to a full-cost charge. */
    public void setOnRedeemedPlus(Predicate<OrderItem> handler) { this.onRedeemedPlus = handler; }

    /**
     * @param orderManager the shared order
     * @param title        heading shown above the list, or null for none
     * @param showTotals   false when the screen shows its own totals (checkout does)
     */
    public CartView(OrderManager orderManager, String title, boolean showTotals) {
        super(8);
        this.orderManager = orderManager;
        setPadding(new Insets(10));

        itemList = new ListView<>(orderManager.getItems());
        itemList.setPlaceholder(new Label("Your cart is empty."));
        itemList.setCellFactory(list -> new CartCell());
        VBox.setVgrow(itemList, Priority.ALWAYS);   // the list takes all spare height; totals stay at the bottom

        if (title != null) {
            Label heading = new Label(title);
            heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
            getChildren().add(heading);
        }
        getChildren().add(itemList);

        if (showTotals) {
            totalLabel.setStyle("-fx-font-weight: bold;");
            getChildren().addAll(subtotalLabel, taxLabel, totalLabel);
            orderManager.getItems().addListener((ListChangeListener<OrderItem>) change -> refreshTotals());
            refreshTotals();
        }
    }

    private void refreshTotals() {
        subtotalLabel.setText(String.format("Subtotal: $%.2f", orderManager.getSubtotal()));
        taxLabel.setText(String.format("Tax: $%.2f", orderManager.getTax()));
        totalLabel.setText(String.format("Total: $%.2f", orderManager.getTotal()));
    }

    /** One cart row: name (and add-ons underneath), - qty +, line price. */
    private class CartCell extends ListCell<OrderItem> {
        private final Label nameLabel = new Label();
        private final Label addOnLabel = new Label();
        private final Label quantityLabel = new Label();
        private final Label priceLabel = new Label();
        private final Button minus = new Button("-");
        private final Button plus = new Button("+");
        private final HBox row;

        CartCell() {
            // Layout: [ - qty + ]  name / add-ons ...........  price
            for (Button b : new Button[]{minus, plus}) {     // small buttons leave room for the description
                b.setStyle("-fx-font-size: 9px; -fx-padding: 0 4 0 4;");
                b.setMinSize(16, 16);
                b.setPrefSize(16, 16);
                b.setFocusTraversable(false);
            }
            quantityLabel.setMinWidth(16);
            quantityLabel.setAlignment(Pos.CENTER);

            nameLabel.setStyle("-fx-font-weight: bold;");
            nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS); // long names end in "..." instead of pushing the price off
            addOnLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b767c;");
            addOnLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            VBox description = new VBox(nameLabel, addOnLabel);
            description.setMinWidth(0);                      // lets the labels shrink and show "..."
            HBox.setHgrow(description, Priority.ALWAYS);

            priceLabel.setMinWidth(Region.USE_PREF_SIZE);    // the price is never cut off

            minus.setOnAction(e -> {
                OrderItem item = getItem();
                if (item == null) return;
                // A redeemed line gives its full redemption price back when a unit leaves the order.
                if (item.isRedeemed() && !onRedeemedMinus.test(item)) return;
                orderManager.changeQuantity(item, -1);
            });
            plus.setOnAction(e -> {
                OrderItem item = getItem();
                if (item == null) return;
                // An extra redeemed unit costs the full redemption price, like redeeming it fresh.
                if (item.isRedeemed() && !onRedeemedPlus.test(item)) return;
                orderManager.changeQuantity(item, +1);
            });

            HBox stepper = new HBox(2, minus, quantityLabel, plus);
            stepper.setAlignment(Pos.CENTER);
            row = new HBox(8, stepper, description, priceLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            setPrefWidth(0);                                 // keeps rows inside the list instead of scrolling sideways
        }

        @Override
        protected void updateItem(OrderItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            nameLabel.setText(item.getItem().getName());
            List<String> names = new ArrayList<>();
            for (Modifier m : item.getChosen()) {
                names.add(m.getName());
            }
            addOnLabel.setText(String.join(", ", names));
            addOnLabel.setManaged(!names.isEmpty());
            addOnLabel.setVisible(!names.isEmpty());
            quantityLabel.setText(String.valueOf(item.getQuantity()));
            priceLabel.setText(String.format("$%.2f", item.getLinePrice()));
            setText(null);
            setGraphic(row);
        }
    }
}
