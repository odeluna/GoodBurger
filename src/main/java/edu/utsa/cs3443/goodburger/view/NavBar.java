package edu.utsa.cs3443.goodburger.view;

import edu.utsa.cs3443.goodburger.GoodBurgerApplication;
import javafx.beans.NamedArg;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.Objects;

/**
 * The bottom navigation bar, shared by every screen so it always looks and works the same.
 *
 * <p>Use it in FXML with {@code <NavBar current="menu" layoutY="500.0" />} (plus
 * {@code <?import edu.utsa.cs3443.goodburger.view.NavBar?>}). {@code current} is the page you are on:
 * its button is highlighted orange and does nothing when clicked. Its look lives in styles/navbar.css.</p>
 */
public class NavBar extends HBox {

    private static final String[][] PAGES = {
            {"home", "home_page.fxml"},
            {"menu", "menu_page.fxml"},
            {"rewards", "rewards_page.fxml"},
            {"checkout", "checkout_page.fxml"},
    };

    public NavBar(@NamedArg("current") String current) {
        getStylesheets().add(Objects.requireNonNull(
                NavBar.class.getResource("/styles/navbar.css")).toExternalForm());
        getStyleClass().add("nav-bar");

        for (String[] page : PAGES) {
            Button button = new Button(page[0]);
            button.setPrefSize(200, 60);
            button.getStyleClass().add("nav-bar-button");
            if (page[0].equalsIgnoreCase(current)) {
                button.getStyleClass().add("nav-bar-current");
            } else {
                String fxml = page[1];
                button.setOnAction(e -> goTo(fxml));
            }
            getChildren().add(button);
        }
    }

    private void goTo(String fxmlFile) {
        try {
            GoodBurgerApplication.switchScreen(this, fxmlFile);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open " + fxmlFile).show();
        }
    }
}
