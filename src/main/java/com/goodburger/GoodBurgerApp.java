package com.goodburger;

import com.goodburger.data.MenuFileReader;
import com.goodburger.model.Menu;
import com.goodburger.order.Order;
import com.goodburger.ui.NavBar;
import com.goodburger.ui.menu.MenuScreen;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Starts the app. JavaFX calls {@link #start} after setting up its window system.
 *
 * <p><b>Startup order:</b> load the menu from the CSV files once, create an empty order, then build the
 * menu screen around both. Loading happens here, not inside the screen, so the screen can be given any
 * menu (for example a small one in a test), and so a broken file is reported before any UI appears.</p>
 *
 * <p><b>Layout:</b> the window is an outer {@link BorderPane}: the current screen goes in the center and the
 * {@link NavBar} goes at the bottom. Keeping the bar outside the screens means it stays put while screens
 * change, and no screen has to include its own copy.</p>
 *
 * <p><b>Where the files are:</b> the paths are relative, so they are looked up from the folder the app
 * runs in. IntelliJ and {@code mvn javafx:run} both run from the project folder, which is where the
 * {@code data} folder is.</p>
 */
public class GoodBurgerApp extends Application {

    static final Path MENU_FILE = Path.of("data", "menu.csv");
    static final Path MODIFIERS_FILE = Path.of("data", "modifiers.csv");

    @Override
    public void start(Stage stage) {
        Menu menu;
        try {
            menu = new MenuFileReader().load(MENU_FILE, MODIFIERS_FILE);
        } catch (IOException e) {
            // Show the problem (missing file, bad line) instead of crashing with a stack trace.
            new Alert(Alert.AlertType.ERROR, "Couldn't load the menu:\n" + e.getMessage()).showAndWait();
            return;
        }

        MenuScreen menuScreen = new MenuScreen(menu, new Order());
        BorderPane root = new BorderPane();
        root.setCenter(menuScreen);                  // the Menu screen is the only one built so far
        root.setBottom(new NavBar("Menu"));          // buttons don't navigate yet; see NavBar.setOnSelect

        stage.setTitle("Good Burger");
        stage.setScene(new Scene(root, 1000, 650));  // 50px taller than before to make room for the bar
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
