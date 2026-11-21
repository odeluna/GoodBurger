package edu.utsa.cs3443.goodburger;

import edu.utsa.cs3443.goodburger.model.Session;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;

import java.io.IOException;

/**
 * Entry point of the GoodBurger app (the launcher; the rewards game is a separate
 * entry point, {@code rewards.RewardsApp}).
 *
 * <p>Builds the one shared {@code model.Session} (menu, accounts, cart), opens the
 * welcome page, and hosts {@code switchScreen}, which every controller calls to swap
 * the FXML pages in place. Read {@code docs/ARCHITECTURE.md} before touching this.</p>
 */
public class GoodBurgerApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        Session.get().loadMenu();   // menu_page.fxml needs the menu; without this MenuManager is null and the page fails to load
        FXMLLoader fxmlLoader = new FXMLLoader(GoodBurgerApplication.class.getResource("home_page.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), Session.WIDTH, Session.HEIGHT);
        stage.setTitle("GoodBurger");
        stage.setScene(scene);
//        stage.setMaximized(true);
        stage.sizeToScene();
        stage.setResizable(false);
        stage.show();
    }

    public static void switchScreen(Node source, String fxmlFile) throws IOException {
        FXMLLoader loader = new FXMLLoader(GoodBurgerApplication.class.getResource(fxmlFile));
        Scene scene = new Scene(loader.load(), Session.WIDTH, Session.HEIGHT);
        Stage stage = (Stage) source.getScene().getWindow();
        stage.setScene(scene);
    }
}
