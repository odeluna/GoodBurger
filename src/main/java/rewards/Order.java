package rewards;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The CSV total is the item subtotal; tax is a separate CSV column. */
public final class Order {
    public static final double TAX_RATE = 0.0825;
    private final String orderId, customerId, pickupTime;
    private final OrderType orderType;
    private final Menu menu;
    private final List<OrderItem> items = new ArrayList<>();

    public Order(String orderId, String customerId, OrderType orderType,
                 String pickupTime, Menu menu) {
        this.orderId = Objects.requireNonNull(orderId);
        this.customerId = Objects.requireNonNull(customerId);
        this.orderType = Objects.requireNonNull(orderType);
        this.pickupTime = Objects.requireNonNull(pickupTime);
        this.menu = Objects.requireNonNull(menu);
    }

    public String orderId() { return orderId; }
    public String customerId() { return customerId; }
    public OrderType orderType() { return orderType; }
    public String pickupTime() { return pickupTime; }
    public List<OrderItem> items() { return List.copyOf(items); }
    public Menu menu() { return menu; }
    public void addItem(OrderItem item) {
        menu.findById(Objects.requireNonNull(item).itemId());
        items.add(item);
    }
    public double subtotal() {
        long cents = 0;
        for (OrderItem item : items) cents += Math.round(item.lineTotal(menu) * 100);
        return cents / 100.0;
    }
    public double tax() { return Math.round(subtotal() * TAX_RATE * 100) / 100.0; }
    public double total() { return subtotal(); }
    public double amountDue() { return total() + tax(); }
    public int totalCalories() { return items.stream().mapToInt(i -> i.calories(menu)).sum(); }
    public int totalProtein() { return items.stream().mapToInt(i -> i.quantity() * menu.findById(i.itemId()).protein()).sum(); }
    public int totalCarbs() { return items.stream().mapToInt(i -> i.quantity() * menu.findById(i.itemId()).carbs()).sum(); }
    public int totalFat() { return items.stream().mapToInt(i -> i.quantity() * menu.findById(i.itemId()).fat()).sum(); }
    public int totalSodium() { return items.stream().mapToInt(i -> i.quantity() * menu.findById(i.itemId()).sodium()).sum(); }
}
