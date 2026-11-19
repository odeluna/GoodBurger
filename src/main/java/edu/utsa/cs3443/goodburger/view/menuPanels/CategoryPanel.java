package edu.utsa.cs3443.goodburger.view.menuPanels;

import edu.utsa.cs3443.goodburger.controller.MenuScreenController;
import edu.utsa.cs3443.goodburger.model.Category;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Left column of the menu screen: one button per {@link Category}. This column never changes.
 *
 * <p><b>Why panels only report clicks:</b> each panel draws one area and calls back to
 * {@link MenuScreenController} when something is clicked, passing what was clicked. It never decides what
 * happens next. All decisions (what to show, what to change) live in MenuScreen, so the panels stay
 * simple and the screen's behavior can be read in one place.</p>
 *
 * <p>The callback is a {@link Consumer}: a "function that takes a Category". MenuScreen passes its
 * own {@code onCategoryClicked} method, written as {@code this::onCategoryClicked}.</p>
 */
public class CategoryPanel extends VBox {

    private final Consumer<Category> onCategoryClicked;
    private final Map<Category, Button> buttons = new EnumMap<>(Category.class);

    /** @param onCategoryClicked called with the category whenever one of its buttons is pressed */
    public CategoryPanel(Consumer<Category> onCategoryClicked) {
        super(10);                                   // 10px gap between buttons
        this.onCategoryClicked = onCategoryClicked;
        setPadding(new Insets(10));
        setPrefWidth(150);
    }

    /**
     * Builds one button per category. Called once at startup with {@link Category#values()}, so a new
     * enum constant automatically gets a button.
     *
     * @param categories the categories to show, in display order
     */
    public void show(Category[] categories) {
        getChildren().clear();
        buttons.clear();
        for (Category c : categories) {
            Button b = new Button(c.getDisplayName());
            b.getStyleClass().add("category-button");   // see menuScreen.css
            buttons.put(c, b);
            b.setMaxWidth(Double.MAX_VALUE);         // stretch to the column width so all buttons line up
            b.setPrefHeight(60);
            b.setOnAction(e -> onCategoryClicked.accept(c));
            getChildren().add(b);
        }
    }

    /**
     * Highlights one category button (the "category-selected" style class, fuchsia in menuScreen.css)
     * and clears the highlight from the others.
     *
     * @param selected the category whose items are showing
     */
    public void setSelected(Category selected) {
        buttons.forEach((c, b) -> {
            b.getStyleClass().remove("category-selected");
            if (c == selected) {
                b.getStyleClass().add("category-selected");
            }
        });
    }
}
