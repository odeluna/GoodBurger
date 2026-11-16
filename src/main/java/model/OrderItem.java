package model;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One line of a customer's order: a {@link MenuItem}, the {@link Modifier}s they chose, and how many.
 *
 * <p><b>Why this class exists:</b> a MenuItem describes what the restaurant sells and must not
 * change, but every customer customizes it differently. OrderItem holds that customer-specific
 * part, so two people can order the same burger with different add-ons without touching the menu.</p>
 *
 * <p><b>Life cycle (how it moves between classes):</b></p>
 * <ol>
 *   <li>The customer clicks an item, and {@link com.goodburger.ui.menu.MenuScreen} creates a new
 *       OrderItem as its "draft".</li>
 *   <li>Ticking checkboxes calls {@link #toggleModifier}; the quantity spinner calls {@link #setQuantity}.
 *       The item preview redraws from {@link #getLinePrice()} after each change.</li>
 *   <li>"Add" hands the finished OrderItem to {@link com.goodburger.order.Order#addItem}; "Cancel" just
 *       drops it.</li>
 * </ol>
 *
 * <p>Prices and nutrition are <i>computed</i> every time they are asked for, never stored, so they can
 * never get out of sync with the chosen add-ons.</p>
 */
public class OrderItem {

    private final MenuItem item;
    private final List<Modifier> chosen = new ArrayList<>();
    private int quantity = 1;   // starts at 1: clicking an item means you want one

    /** @param item the menu item being customized */
    public OrderItem(MenuItem item) {
        this.item = item;
    }

    /**
     * Adds the add-on if it isn't chosen yet, or removes it if it is: the same behavior as a checkbox.
     *
     * @param m one of {@link MenuItem#getModifiers()} for this item
     * @throws IllegalArgumentException if {@code m} isn't offered for this item, which would mean a
     *         screen is showing the wrong checkboxes
     */
    public void toggleModifier(Modifier m) {
        if (!item.getModifiers().contains(m)) {
            throw new IllegalArgumentException(m.getName() + " is not an option for " + item.getName());
        }
        if (!chosen.remove(m)) {
            chosen.add(m);
        }
    }

    /**
     * @param q how many of this exact customized item to order
     * @throws IllegalArgumentException if {@code q} is less than 1; removing an item is done by
     *         cancelling it, not by setting the quantity to 0
     */
    public void setQuantity(int q) {
        if (q < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        quantity = q;
    }

    /** @return (base price + every chosen add-on's price) x quantity */
    public double getLinePrice() {
        double each = item.getPrice();
        for (Modifier m : chosen) {
            each += m.getExtraPrice();
        }
        return each * quantity;
    }

    /** @return the item's nutrition plus every chosen add-on, multiplied by quantity */
    public Nutrition getNutrition() {
        Nutrition each = item.getNutrition();
        for (Modifier m : chosen) {
            each = each.add(m.getNutrition());
        }
        Nutrition total = Nutrition.ZERO;
        for (int i = 0; i < quantity; i++) {
            total = total.add(each);
        }
        return total;
    }

    public MenuItem getItem() { return item; }
    public int getQuantity()  { return quantity; }

    /** @return the chosen add-ons, read-only; change them with {@link #toggleModifier} */
    public List<Modifier> getChosen() {
        return Collections.unmodifiableList(chosen);
    }

    /** Label used in the cart list, for example "2 x Classic Burger [Add bacon]  $16.98". */
    @Override
    public String toString() {
        String extras = chosen.isEmpty() ? "" : " " + chosen.stream().map(Modifier::getName).toList();
        return String.format("%d x %s%s  $%.2f", quantity, item.getName(), extras, getLinePrice());
    }
}
