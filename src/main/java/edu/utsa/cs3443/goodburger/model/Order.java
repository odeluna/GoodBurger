package edu.utsa.cs3443.goodburger.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * One customer order: the list of OrderItems plus who ordered it and when they want it.
 *
 * <p>The item list is an ObservableList so any screen showing it (the cart on the menu page,
 * the checkout list) redraws by itself when an item is added, changed or removed.
 * Screens should not change the list directly; they go through {@link OrderManager}.</p>
 */
public class Order {

    private final ObservableList<OrderItem> items = FXCollections.observableArrayList();
    private String orderId = "";
    private String customerId = "";
    private OrderType orderType = OrderType.ASAP;
    private String pickupTime = "ASAP";

    public Order() {
    }

    public Order(String orderId, String customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
    }

    public void clear() {
        items.clear();
        orderId = "";
        customerId = "";
        orderType = OrderType.ASAP;
        pickupTime = "ASAP";
    }

    /** Sum of every line price, before tax. */
    public double getSubtotal() {
        double subtotal = 0;
        for (OrderItem item : items) {
            subtotal += item.getLinePrice();
        }
        return subtotal;
    }

    public Nutrition getNutrition() {
        Nutrition total = Nutrition.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.getNutrition());
        }
        return total;
    }

    public ObservableList<OrderItem> getItems() { return items; }
    public String getOrderId()                  { return orderId; }
    public String getCustomerId()               { return customerId; }
    public OrderType getOrderType()             { return orderType; }
    public String getPickupTime()               { return pickupTime; }

    public void setOrderId(String orderId)          { this.orderId = orderId; }
    public void setCustomerId(String customerId)    { this.customerId = customerId; }
    public void setOrderType(OrderType orderType)   { this.orderType = orderType; }
    public void setPickupTime(String pickupTime)    { this.pickupTime = pickupTime; }
}
