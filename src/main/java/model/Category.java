package model;
/**
 * The four menu topics a customer can browse: the buttons in the left column of the menu screen.
 *
 * <p><b>Why an enum and not a String?</b> The set of categories is fixed and small. An enum
 * lets the compiler catch typos ({@code Category.ENTRE} will not compile, while {@code "Entre"}
 * would silently match nothing), and it gives every category a stable name that can be stored
 * in menu.csv and turned back into a value with {@link #valueOf(String)}.</p>
 *
 * <p><b>Who uses it:</b> every {@link MenuItem} has exactly one Category; {@link Menu#getByCategory}
 * filters items by it; the category buttons are built by looping over {@link #values()}, so
 * adding a constant here adds a button automatically.</p>
 */
public enum Category {
    ENTREE("Entrees"),
    SIDE("Sides"),
    DRINK("Drinks"),
    DESSERT("Desserts");

    /** Text shown on the category button. Kept separate from the constant name so the CSV can stay all caps. */
    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    /** @return the label to show on screen, for example "Entrees" for {@link #ENTREE} */
    public String getDisplayName() {
        return displayName;
    }
}
