package edu.utsa.cs3443.goodburger.view.menuPanels;

import edu.utsa.cs3443.goodburger.model.MenuItem;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.TilePane;

import java.util.List;
import java.util.function.Consumer;

/**
 * Middle area, first view: a grid with one button per item in the selected category.
 *
 * <p><b>Why buttons are made in code:</b> the number of items comes from menu.csv (8 burgers, 4 sides,
 * ...). Creating buttons in a loop means adding a line to the file adds a button, with no layout changes.
 * The {@link TilePane} wraps buttons onto new rows automatically, and the {@link ScrollPane} around it
 * adds a scroll bar if there are more items than fit.</p>
 *
 * <p>Like every panel, it only draws and reports clicks; see {@link CategoryPanel} for why.</p>
 */
public class ItemGridPanel extends ScrollPane {

    private final TilePane grid = new TilePane(10, 10);   // 10px gaps between tiles
    private final Consumer<MenuItem> onItemClicked;

    /** @param onItemClicked called with the item whenever its button is pressed */
    public ItemGridPanel(Consumer<MenuItem> onItemClicked) {
        this.onItemClicked = onItemClicked;
        grid.setPadding(new Insets(10));
        grid.setPrefColumns(2);       // two columns like the Figma layout; it wraps to more if the window is wide
        setContent(grid);
        setFitToWidth(true);          // let the grid use the full width so tiles wrap correctly
    }

    /**
     * Replaces the grid's buttons with one per item.
     *
     * @param items the items to show, usually {@code menu.getByCategory(selectedCategory)}
     */
    public void show(List<MenuItem> items) {
        grid.getChildren().clear();
        for (MenuItem item : items) {
            Button b = new Button(String.format("%s%n$%.2f", item.getName(), item.getPrice()));
            b.setPrefSize(150, 70);
            b.setWrapText(true);
            b.setOnAction(e -> onItemClicked.accept(item));
            grid.getChildren().add(b);
        }
    }
}
