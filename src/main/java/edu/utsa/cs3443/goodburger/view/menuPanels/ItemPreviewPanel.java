package edu.utsa.cs3443.goodburger.view.menuPanels;


import edu.utsa.cs3443.goodburger.controller.MenuScreenController;
import edu.utsa.cs3443.goodburger.model.OrderItem;
import edu.utsa.cs3443.goodburger.model.Modifier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Right area, second view: only the item being customized, with its add-ons, price, and the
 * Cancel and Add buttons.
 *
 * <p>It is shown instead of the cart while the customer is on the item detail view, so they can see
 * exactly what the add-ons cost before committing. {@link MenuScreenController} calls {@link #show} again after
 * every checkbox or quantity change, so the price updates live.</p>
 *
 * <p>The buttons take {@link Runnable}s ("a function with no inputs") because Add and Cancel don't need to
 * pass anything back: MenuScreen already holds the draft. See {@link CategoryPanel} for why panels work
 * this way.</p>
 */
public class ItemPreviewPanel extends VBox {

    private final Label nameLabel = new Label();
    private final VBox addOnLines = new VBox(4);
    private final Label caloriesLabel = new Label();
    private final Label totalLabel = new Label();

    /**
     * @param onAdd    called when "Add" is pressed
     * @param onCancel called when "Cancel" is pressed
     */
    public ItemPreviewPanel(Runnable onAdd, Runnable onCancel) {
        super(8);
        setPadding(new Insets(10));
        setPrefWidth(260);
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        totalLabel.setStyle("-fx-font-weight: bold;");

        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> onCancel.run());
        Button add = new Button("Add");
        add.setDefaultButton(true);   // Enter key also adds
        add.setOnAction(e -> onAdd.run());
        HBox buttons = new HBox(10, cancel, add);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();                // pushes the totals and buttons to the bottom
        VBox.setVgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(nameLabel, addOnLines, spacer, caloriesLabel, totalLabel, buttons);
    }

    /**
     * Redraws the preview for the draft item.
     *
     * @param draft the item being customized
     */
    public void show(OrderItem draft) {
        nameLabel.setText(String.format("%s  $%.2f", draft.getItem().getName(), draft.getItem().getPrice()));
        addOnLines.getChildren().clear();
        for (Modifier m : draft.getChosen()) {
            addOnLines.getChildren().add(new Label(String.format("  + %s  $%.2f", m.getName(), m.getExtraPrice())));
        }
        caloriesLabel.setText(draft.getNutrition().getCalories() + " cal");
        totalLabel.setText(String.format("Item total (x%d): $%.2f", draft.getQuantity(), draft.getLinePrice()));
    }
}
