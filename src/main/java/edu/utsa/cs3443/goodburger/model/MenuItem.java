package edu.utsa.cs3443.goodburger.model;

import edu.utsa.cs3443.goodburger.model.MenuManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One food option on the menu, such as "Classic Burger". One line of menu.csv becomes one MenuItem.
 *
 * <p><b>Relationships (see the UML):</b></p>
 * <ul>
 *   <li>has exactly one {@link Category}, which decides which category button shows it;</li>
 *   <li>owns one {@link Nutrition} (composition: the facts make no sense without the item);</li>
 *   <li>owns zero or more {@link Modifier}s, the add-ons offered for this item only;</li>
 *   <li>is collected by {@link Menu} and referred to by {@link OrderItem} when a customer picks it.</li>
 * </ul>
 *
 * <p><b>Why no setters?</b> The menu is read from the file once and then only read, never edited,
 * by the app. The one exception is {@link #addModifier}, which exists because modifiers come from a
 * second file and are attached after the item is created.</p>
 */
public class MenuItem {

    private final String id;          // short unique key, e.g. "B01"; modifiers.csv and other teams' files refer to items by this
    private final String name;
    private final Category category;
    private final double price;       // base price in dollars, before add-ons
    private final Nutrition nutrition;
    private final List<Modifier> modifiers = new ArrayList<>();
    private final boolean cateringOnly; // true = only shown on the catering menu, hidden from the normal one

    /**
     * Creates an item with no add-ons yet. {@link MenuFileReader} attaches
     * add-ons afterwards with {@link #addModifier}.
     */
    public MenuItem(String id, String name, Category category, double price,
                    Nutrition nutrition, boolean cateringOnly) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.nutrition = nutrition;
        this.cateringOnly = cateringOnly;
    }

    /**
     * Attaches an add-on to this item. Called by the file reader for each matching line of modifiers.csv.
     *
     * @param m the add-on to offer for this item
     */
    public void addModifier(Modifier m) {
        modifiers.add(m);
    }

    public String getId()            { return id; }
    public String getName()          { return name; }
    public Category getCategory()    { return category; }
    public double getPrice()         { return price; }
    public Nutrition getNutrition()  { return nutrition; }
    public boolean isCateringOnly()  { return cateringOnly; }

    /**
     * @return the add-ons for this item, read-only. Returning an unmodifiable view means screens
     *         can loop over the list to build checkboxes but can't accidentally change the menu.
     */
    public List<Modifier> getModifiers() {
        return Collections.unmodifiableList(modifiers);
    }

    @Override
    public String toString() {
        return String.format("%s ($%.2f)", name, price);
    }
}
