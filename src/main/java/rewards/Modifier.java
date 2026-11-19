package rewards;

import java.util.Objects;

/** A price and calorie change allowed for one catalog item. */
public record Modifier(String itemId, String name, double extraPrice, int extraCalories) {
    public Modifier {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(name);
    }
}
