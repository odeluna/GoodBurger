package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import edu.utsa.cs3443.goodburger.MenuPanels.*;

/**
 * The customer's current order.
 *
 * <p><b>Placeholder:</b> Order belongs to the checkout part of the app, which a teammate owns. This
 * minimal version exists only so the food menu compiles and runs on its own. It contains exactly
 * the two methods the menu relies on; when the real Order class arrives, it just needs to keep
 * {@link #addItem} and {@link #getItems} and this file can be replaced.</p>
 *
 * <p><b>How the menu uses it:</b> {@link MenuPanels/edu.utsa.cs3443.goodburger.MenuPanels.MenuScreenController} calls {@link #addItem}
 * when the customer presses "Add", and {@link CartPanel} reads
 * {@link #getItems} to draw the cart.</p>
 */
public class Order {

    private final List<OrderItem> items = new ArrayList<>();

    /** @param item a finished, customized item from the menu screen */
    public void addItem(OrderItem item) {
        items.add(item);
    }

    /** @return the order's items, read-only, in the order they were added */
    public List<OrderItem> getItems() {
        return items;
    }
}