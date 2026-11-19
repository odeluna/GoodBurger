package rewards;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Owns the in-progress order; persistence is delegated to DataManager. */
public final class OrderManager {
    private Order currentOrder;
    private final List<MenuItem> menu;

    public OrderManager(List<MenuItem> menu) { this.menu = List.copyOf(Objects.requireNonNull(menu)); }
    public Order currentOrder() { return currentOrder; }
    public Order newOrder(String customerId, OrderType type, String pickupTime) {
        currentOrder = new Order("O" + UUID.randomUUID().toString().replace("-", ""),
                customerId, type, pickupTime, new Menu(menu));
        return currentOrder;
    }
    public void addToOrder(String itemId, int quantity, List<Modifier> mods) {
        if (currentOrder == null) throw new IllegalStateException("Start an order first");
        MenuItem item = currentOrder.menu().findById(itemId);
        if (mods.stream().distinct().count() != mods.size())
            throw new IllegalArgumentException("Duplicate modifier");
        for (Modifier modifier : mods) {
            if (item.modifiers().stream().noneMatch(allowed -> allowed.equals(modifier)))
                throw new IllegalArgumentException("Modifier is not allowed for " + itemId);
        }
        currentOrder.addItem(new OrderItem(itemId, quantity, mods));
    }
    public double calculateTotal() {
        if (currentOrder == null) throw new IllegalStateException("Start an order first");
        return currentOrder.amountDue();
    }
    public Order checkout() {
        if (currentOrder == null || currentOrder.items().isEmpty())
            throw new IllegalStateException("Cannot checkout an empty order");
        return currentOrder;
    }
}
