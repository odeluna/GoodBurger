package edu.utsa.cs3443.goodburger.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;

/**
 * Owns the customer's current order: adding items, changing quantities, totals and checkout.
 *
 * <p>Every screen shares the one OrderManager kept in {@link Session}, so the cart looks the
 * same on the menu page and the checkout page. Screens get a read-only view of the items and
 * make changes through the methods here.</p>
 */
public class OrderManager {

    /** San Antonio sales tax, 8.25%. */
    public static final double TAX_RATE = 0.0825;

    private final DataManager dataManager;
    private final Order currentOrder = new Order();   // never replaced, so screens can keep listening to it
    private final ObservableList<OrderItem> readOnlyItems =
            FXCollections.unmodifiableObservableList(currentOrder.getItems());

    public OrderManager(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void addItem(OrderItem item) {
        currentOrder.addItem(item);
    }

    /**
     * Adds delta (usually +1 or -1) to an item's quantity. Going down to zero removes the item.
     */
    public void changeQuantity(OrderItem item, int delta) {
        ObservableList<OrderItem> items = currentOrder.getItems();
        int index = items.indexOf(item);
        if (index < 0) {
            return;
        }
        int newQuantity = item.getQuantity() + delta;
        if (newQuantity <= 0) {
            items.remove(index);
        } else {
            item.setQuantity(newQuantity);
            items.set(index, item);   // tells every ListView showing the cart to redraw this row
        }
    }

    public void removeItem(OrderItem item) {
        currentOrder.removeItem(item);
    }

    public ObservableList<OrderItem> getItems() { return readOnlyItems; }
    public Order getCurrentOrder()              { return currentOrder; }
    public boolean isEmpty()                    { return currentOrder.getItems().isEmpty(); }

    public double getSubtotal() { return currentOrder.getSubtotal(); }
    public double getTax()      { return round(getSubtotal() * TAX_RATE); }
    public double getTotal()    { return round(getSubtotal() + getTax()); }

    /**
     * Saves the current order to orders.csv and empties the cart.
     *
     * @param user       the signed-in user, or null/guest
     * @param pickupTime what the customer picked on the checkout page
     * @return the order id that was saved
     */
    public String checkout(User user, OrderType type, String pickupTime) throws IOException {
        if (isEmpty()) {
            throw new IllegalStateException("The cart is empty");
        }
        currentOrder.setOrderId(dataManager.nextOrderId());
        currentOrder.setCustomerId(user == null || user.isGuest() ? "guest" : user.getId());
        currentOrder.setOrderType(type);
        currentOrder.setPickupTime(pickupTime);
        dataManager.saveOrder(currentOrder, getSubtotal(), getTax(), getTotal());
        String id = currentOrder.getOrderId();
        currentOrder.clear();
        return id;
    }

    private static double round(double dollars) {
        return Math.round(dollars * 100) / 100.0;
    }
}
