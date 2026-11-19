package model;

/**
 * Nutrition facts for one menu item, or a total of several items.
 *
 * <p><b>Why a separate class?</b> The same five numbers are needed in three places: on the item
 * detail view, on the item preview (with add-ons included), and at checkout for the whole order.
 * Grouping them into one object means those places pass a single value around instead of five
 * loose ints, and the adding logic lives in exactly one method ({@link #add}).</p>
 *
 * <p><b>Why immutable (all fields final, no setters)?</b> A {@link MenuItem}'s nutrition must never
 * change just because someone computed a total from it. {@link #add} therefore returns a
 * <i>new</i> Nutrition instead of changing this one, so it is always safe to share.</p>
 *
 * <p><b>Who uses it:</b> {@link MenuItem} owns one (composition in the UML); {@link OrderItem#getNutrition()}
 * adds them up; the checkout teammate can sum an entire order the same way.</p>
 */
public class Nutrition {

    /** A Nutrition of all zeros: the starting point when adding several together. */
    public static final Nutrition ZERO = new Nutrition(0, 0, 0, 0, 0);

    private final int calories;
    private final int protein;   // grams
    private final int carbs;     // grams
    private final int fat;       // grams
    private final int sodium;    // milligrams

    /**
     * @param calories kcal
     * @param protein  grams of protein
     * @param carbs    grams of carbohydrates
     * @param fat      grams of fat
     * @param sodium   milligrams of sodium
     */
    public Nutrition(int calories, int protein, int carbs, int fat, int sodium) {
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.sodium = sodium;
    }

    /**
     * Adds two nutrition values field by field.
     *
     * @param other the values to add to this one
     * @return a new Nutrition holding the sum; neither this object nor {@code other} is changed
     */
    public Nutrition add(Nutrition other) {
        return new Nutrition(
                calories + other.calories,
                protein + other.protein,
                carbs + other.carbs,
                fat + other.fat,
                sodium + other.sodium);
    }

    public int getCalories() { return calories; }
    public int getProtein()  { return protein; }
    public int getCarbs()    { return carbs; }
    public int getFat()      { return fat; }
    public int getSodium()   { return sodium; }

    /** One-line summary used by the UI, for example "650 cal | 32g protein | 45g carbs | 35g fat | 980mg sodium". */
    @Override
    public String toString() {
        return calories + " cal | " + protein + "g protein | " + carbs + "g carbs | "
                + fat + "g fat | " + sodium + "mg sodium";
    }
}