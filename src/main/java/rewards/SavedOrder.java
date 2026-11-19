package rewards;

import java.util.Objects;

/** Thin wrapper: keeps favorite metadata separate from the underlying Order. */
public record SavedOrder(String userId, Order order, String addedAt) {
    public SavedOrder {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(order);
        Objects.requireNonNull(addedAt);
    }
    public String orderId() { return order.orderId(); }
}
