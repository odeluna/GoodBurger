package edu.utsa.cs3443.goodburger.view.menuPanels;

import edu.utsa.cs3443.goodburger.controller.MenuScreenController;
import edu.utsa.cs3443.goodburger.model.MenuItem;
import edu.utsa.cs3443.goodburger.model.Modifier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Middle area, second view: details for one item, with its photo, its description, a checkbox for
 * each add-on, and a quantity picker.
 *
 * <p><b>Why checkboxes are made in code:</b> each item has its own number of add-ons (from modifiers.csv),
 * so the list is rebuilt from {@link MenuItem#getModifiers()} every time an item is shown.</p>
 *
 * <p><b>Why the photo is cropped in code:</b> the menu photos are not uniform in size (a burger shot
 * is wide, a cookie shot is square), so the {@link ImageView} is scaled to <i>cover</i> a fixed
 * {@value #PHOTO_BOX}x{@value #PHOTO_BOX} box (the {@code max} of the two scales fills the box, the
 * {@link Rectangle} clip trims the overflow) and centered in it. Every item therefore gets a photo of
 * the exact same size, with no distortion. Items without a photo show the plain placeholder box.</p>
 *
 * <p>Ticking a box or changing the quantity does not change any data here. The panel reports the change
 * to {@link MenuScreenController}, which updates the draft {@link edu.utsa.cs3443.goodburger.model.OrderItem} and redraws the
 * preview on the right. See {@link CategoryPanel} for why panels work this way.</p>
 */
public class ItemDetailPanel extends ScrollPane {

    /** Side of the square photo box, in pixels. */
    private static final double PHOTO_BOX = 220;

    private final ImageView photoView = new ImageView();
    private final StackPane photoBox;
    private final Label nameLabel = new Label();
    private final Label descriptionLabel = new Label();
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
        descriptionLabel.setWrapText(true);
        nutritionLabel.setWrapText(true);

        // The photo box is created once and reused: a fixed-size StackPane centered on the image.
        photoView.setPreserveRatio(true);
        photoBox = new StackPane(photoView);
        photoBox.setPrefSize(PHOTO_BOX, PHOTO_BOX);
        photoBox.setMaxSize(PHOTO_BOX, PHOTO_BOX);
        photoBox.setAlignment(Pos.CENTER);
        photoBox.getStyleClass().add("item-photo");   // placeholder background, see styles/menuScreen.css

        // The spinner is created once and reused; it reports every change through the callback.
        quantitySpinner.valueProperty().addListener((obs, oldQ, newQ) -> onQuantityChanged.accept(newQ));

        VBox content = new VBox(10,
                photoBox, nameLabel, descriptionLabel, priceLabel, nutritionLabel,
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
        showPhoto(item.getPhoto());
        nameLabel.setText(item.getName());
        descriptionLabel.setText(item.getDescription());
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

    /**
     * Puts the item's photo into the fixed photo box, scaled to cover it and cropped to the box.
     * A missing or empty photo leaves the placeholder background showing instead.
     *
     * @param fileName the photo file name from {@link MenuItem#getPhoto()}, e.g. "01B_RowdyBurger.jpg"
     */
    private void showPhoto(String fileName) {
        photoBox.setClip(null);
        photoView.setX(0);
        photoView.setY(0);
        photoView.setFitWidth(0);
        photoView.setFitHeight(0);
        if (fileName == null || fileName.isBlank()) {
            photoView.setImage(null);
            return;
        }
        var url = getClass().getResource("/images/" + fileName);
        if (url == null) {
            photoView.setImage(null);   // no such resource: keep the placeholder, don't crash the panel
            return;
        }
        Image image = new Image(url.toExternalForm());
        double w = image.getWidth();
        double h = image.getHeight();
        if (w <= 0 || h <= 0) {
            // Background not fully decoded yet (rare for file images): fit inside instead of cropping.
            photoView.setFitWidth(PHOTO_BOX);
            photoView.setFitHeight(PHOTO_BOX);
            photoView.setImage(image);
            return;
        }
        double scale = Math.max(PHOTO_BOX / w, PHOTO_BOX / h);   // max = cover; min would letterbox
        photoView.setFitWidth(w * scale);
        photoView.setFitHeight(h * scale);
        photoView.setX((PHOTO_BOX - w * scale) / 2);   // center the oversized image in the box
        photoView.setY((PHOTO_BOX - h * scale) / 2);
        photoView.setImage(image);
        Rectangle clip = new Rectangle(0, 0, PHOTO_BOX, PHOTO_BOX);   // trim the overflow
        clip.setArcWidth(10);                                          // rounded corners
        clip.setArcHeight(10);
        photoBox.setClip(clip);
    }
}
