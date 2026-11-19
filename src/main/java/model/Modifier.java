package model;

/**
 * One optional add-on or change for a menu item, such as "Add bacon" or "No onions".
 *
 * <p>Each line of modifiers.csv becomes one Modifier, and it is attached to the {@link MenuItem}
 * whose id is in that line. Different items can therefore have a different number of add-ons,
 * which is what lets the item detail view build one checkbox per Modifier instead of a fixed layout.</p>
 *
 * <p><b>Why only price and calories?</b> Those are the two numbers the item preview has to update
 * live when a box is ticked. Protein, carbs, etc. for add-ons can be added later without
 * changing any other class, because callers only go through {@link #getNutrition()}.</p>
 *
 * <p><b>Why immutable?</b> The same Modifier object is shared by the menu and by every
 * {@link OrderItem} that picked it, so it must never change after loading.</p>
 */
public class Modifier {

    private final String name;
    private final double extraPrice;   // dollars added to the item price; 0 for free changes like "No onions"
    private final int extraCalories;   // can be negative when the change removes something

    /**
     * @param name          text shown next to the checkbox
     * @param extraPrice    dollars added per item when chosen
     * @param extraCalories calories added (or removed, if negative) when chosen
     */
    public Modifier(String name, double extraPrice, int extraCalories) {
        this.name = name;
        this.extraPrice = extraPrice;
        this.extraCalories = extraCalories;
    }

    public String getName()        { return name; }
    public double getExtraPrice()  { return extraPrice; }
    public int getExtraCalories()  { return extraCalories; }

    /**
     * Converts this add-on's calorie change into a {@link Nutrition} so it can be summed with
     * {@link Nutrition#add} exactly like a whole item.
     *
     * @return a Nutrition with only the calories filled in
     */
    public Nutrition getNutrition() {
        return new Nutrition(extraCalories, 0, 0, 0, 0);
    }

    /** Label used on checkboxes and in the item preview, for example "Add bacon (+$1.50)". */
    @Override
    public String toString() {
        return extraPrice > 0 ? String.format("%s (+$%.2f)", name, extraPrice) : name;
    }
}
