package edu.utsa.cs3443.goodburger.model;
import edu.utsa.cs3443.goodburger.view.menuPanels.ItemDetailPanel;
/**
 * A single optional add-on or change for a menu item, such as " - Add bacon" or " - No onions".
 *
 * <p>Each line of modifiers.csv becomes one Modifier object, and it is attached to the {@link MenuItem}
 * by linking the itemId in each line. Different items can therefore have a different number of add-ons,
 * which allows {@link ItemDetailPanel} to build one checkbox per Modifier instead of a fixed layout.</p>
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
    private final int extraProtein;
    private final int extraCarbs;
    private final int extraFat;
    private final int extraSodium;


    /**
     * @param name          Text shown next to the checkbox
     * @param extraPrice    Dollars added per item when chosen
     * @param extraCalories Calories added/subracted
     * @param extraProtein  Protein added/subracted
     * @param extraCarbs    Carbs added/subracted
     * @param extraFat      Fat added/subracted
     * @param extraSodium   Sodium added/subracted
     */

    public Modifier(String name, double extraPrice, int extraCalories, int extraProtein, int extraCarbs, int extraFat, int extraSodium) {
        this.name = name;
        this.extraPrice = extraPrice;
        this.extraCalories = extraCalories;
        this.extraProtein = extraProtein;
        this.extraCarbs = extraCarbs;
        this.extraFat = extraFat;
        this.extraSodium = extraSodium;
    }
    public String getName()        { return name; }
    public double getExtraPrice()  { return extraPrice; }
    /**
     * Converts this add-ons' nutritional change into a {@link Nutrition} so it can be summed with
     * {@link Nutrition#add} exactly like a whole item.
     *
     * @return a Nutrition(calories, protein, carbs, fat, sodium)
     */
    public Nutrition getNutrition() {
        return new Nutrition(extraCalories, extraProtein, extraCarbs, extraFat, extraSodium);
    }

    /** Label used on checkboxes and in the item preview, for example "Add bacon (+$1.50)". */
    @Override
    public String toString() {
        return extraPrice > 0 ? String.format("%s (+$%.2f)", name, extraPrice) : name;
    }
}
