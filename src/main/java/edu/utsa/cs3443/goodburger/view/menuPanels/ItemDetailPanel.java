package edu.utsa.cs3443.goodburger.view.menuPanels;

import edu.utsa.cs3443.goodburger.model.MenuItem;
import edu.utsa.cs3443.goodburger.model.Modifier;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Middle area, second view: details for one item, with a checkbox for each add-on and a quantity picker.
 *
 * <p><b>Why checkboxes are made in code:</b> each item has its own number of add-ons (from modifiers.csv),
 * so the list is rebuilt from {@link MenuItem#getModifiers()} every time an item is shown.</p>
 *
 * <p>Ticking a box or changing the quantity does not change any data here. The panel reports the change
 * to {@link MenuScreenController}, which updates the draft {@link edu.utsa.cs3443.goodburger.model.OrderItem} and redraws the
 * preview on the right. See {@link CategoryPanel} for why panels work this way.</p>
 */
public class ItemDetailPanel extends ScrollPane {

    private final Label nameLabel = new Label();
    private final Label priceLabel = new Label();
    private final Label nutritionLabel = new Label();
    private final VBox addOnList = new VBox(6);
    private final Spinner<Integer> quantitySpinner = new Spinner<>(1, 20, 1);  // min 1, max 20, start at 1

    private final Consumer<Modifier> onModifierToggled;

    /**
     * @param onModifierToggled called with the add-on whenever its checkbox is ticked or unticked
     * @param onQuantityChanged called with the new quantity whenever the spinner changes
     */
    public ItemDetailPanel(Consumer<Modifier> onModifierToggled, IntConsumer onQuantityChanged) {
        this.onModifierToggled = onModifierToggled;
        nameLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        nutritionLabel.setWrapText(true);

        // The spinner is created once and reused; it reports every change through the callback.
        quantitySpinner.valueProperty().addListener((obs, oldQ, newQ) -> onQuantityChanged.accept(newQ));

        VBox content = new VBox(10,
                nameLabel, priceLabel, nutritionLabel,
                new Label("Add-ons:"), addOnList,
                new HBox(8, new Label("Quantity:"), quantitySpinner));
        content.setPadding(new Insets(10));
        setContent(content);
        setFitToWidth(true);
    }

    /**
     * Fills the view for one item. Called each time a new item is clicked, so it resets the quantity and
     * builds fresh, unticked checkboxes to match the new, empty draft.
     *
     * @param item the item the customer clicked
     */
    public void show(MenuItem item) {
        nameLabel.setText(item.getName());
        priceLabel.setText(String.format("$%.2f", item.getPrice()));
        nutritionLabel.setText(item.getNutrition().toString());

        addOnList.getChildren().clear();
        if (item.getModifiers().isEmpty()) {
            addOnList.getChildren().add(new Label("No add-ons for this item."));
        }
        for (Modifier m : item.getModifiers()) {
            CheckBox cb = new CheckBox(m.toString());
            cb.setOnAction(e -> onModifierToggled.accept(m));
            addOnList.getChildren().add(cb);
        }

        // Reset to 1 for the new item. This fires the listener, which sets the draft's quantity to 1: harmless.
        quantitySpinner.getValueFactory().setValue(1);
        setVvalue(0);   // scroll back to the top
    }
}
