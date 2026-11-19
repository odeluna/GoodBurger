package rewards;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Catalog data and its available customizations; no persistence or UI logic. */
public final class MenuItem {
    private final String id, name;
    private final Category category;
    private final double price;
    private final int calories, protein, carbs, fat, sodium;
    private final boolean cateringOnly;
    private final List<Modifier> modifiers = new ArrayList<>();

    public MenuItem(String id, String name, Category category, double price, int calories,
                    int protein, int carbs, int fat, int sodium, boolean cateringOnly) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.category = Objects.requireNonNull(category);
        if (price < 0) throw new IllegalArgumentException("Negative menu price");
        this.price = price;
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.sodium = sodium;
        this.cateringOnly = cateringOnly;
    }

    public String id() { return id; }
    public String name() { return name; }
    public Category category() { return category; }
    public double price() { return price; }
    public int calories() { return calories; }
    public int protein() { return protein; }
    public int carbs() { return carbs; }
    public int fat() { return fat; }
    public int sodium() { return sodium; }
    public boolean cateringOnly() { return cateringOnly; }
    public List<Modifier> modifiers() { return List.copyOf(modifiers); }

    public void addModifier(Modifier modifier) {
        Objects.requireNonNull(modifier);
        if (!id.equals(modifier.itemId())) throw new IllegalArgumentException("Wrong modifier item");
        modifiers.add(modifier);
    }
}
