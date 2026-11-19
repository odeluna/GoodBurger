package edu.utsa.cs3443.goodburger.model;
/**
 * Four available menu topics to browse:
 *  - These are the buttons on the left side of the menu_page.fxml
 *  <li> ENTREE, SIDE, DRINK, DESSERT</li>
 * <p><b>Who uses it:</b> every {@link MenuItem} has exactly one Category; {@link MenuManager#getByCategory}
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
