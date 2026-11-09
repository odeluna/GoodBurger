package model;

import java.util.ArrayList;

public class Order {

    private ArrayList<MenuItem> orderItems;
    private String orderID;
    private String customerID;
    private double total;
    private double tax;
    private String pickupTime;
    private Nutrition nutrition;

    public Order(ArrayList<MenuItem> orderItems, String orderID, String customerID, double total, double tax, String pickupTime) {
        this.orderItems = orderItems;
        this.orderID = orderID;
        this.customerID = customerID;
        this.total = total;
        this.tax = tax;
        this.pickupTime = pickupTime;
    }

    public Order(String orderID, String customerID) {
        this.orderItems = new ArrayList<>();
        this.orderID = orderID;
        this.customerID = customerID;
        this.total = 0;
        this.tax = 0;
        this.pickupTime = "";
    }

    public ArrayList<MenuItem> getOrderItems() {
        return orderItems;
    }

    public void addItem(MenuItem item) {
        orderItems.add(item);
    }
    public void removeItem(MenuItem item) {
        orderItems.remove(item);
    }
    public void clearOrder() {
        orderItems.clear();
    }




}
