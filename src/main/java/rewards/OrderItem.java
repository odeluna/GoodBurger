package rewards;

import java.util.List;
import java.util.Objects;

/** One order line; nutrition and price are derived from its catalog item. */
public final class OrderItem {
    private final String itemId;
    private final int quantity;
    private final List<Modifier> modifiers;

    public OrderItem(String itemId, int quantity, List<Modifier> modifiers) {
        this.itemId = Objects.requireNonNull(itemId);
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        this.quantity = quantity;
        this.modifiers = List.copyOf(Objects.requireNonNull(modifiers));
        if (this.modifiers.stream().anyMatch(m -> !itemId.equals(m.itemId())))
            throw new IllegalArgumentException("Modifier belongs to another item");
    }

    public String itemId() { return itemId; }
    public int quantity() { return quantity; }
    public List<Modifier> modifiers() { return modifiers; }
    public double lineTotal(Menu menu) {
        long cents = Math.round(menu.findById(itemId).price() * 100);
        for (Modifier modifier : modifiers) cents += Math.round(modifier.extraPrice() * 100);
        return quantity * cents / 100.0;
    }
    public int calories(Menu menu) {
        int each = menu.findById(itemId).calories();
        for (Modifier modifier : modifiers) each += modifier.extraCalories();
        return quantity * each;
    }
}
