package edu.utsa.cs3443.beef_login;

import edu.utsa.cs3443.beef_login.model.User;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * GoodBurgerApplication serves as the main entry point for the Good Burger app,
 * initializing and launching the JavaFX user interface.
 *
 * author: Anthony Lassiter
 * class: CS3443
 */
public class GoodBurgerApplication extends Application {

    private static User currentUser;

    // Starts the primary application stage and loads the sign in screen
    @Override
    public void start (Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(GoodBurgerApplication.class.getResource("sign_in_page.fxml"));
        Scene scene = new Scene(loader.load());

        stage.setTitle("Good Burger");
        stage.setScene(scene);
        stage.show();
    }

    // Loads the given fxml file and shows it in the window that contains source
    public static void switchScreen(Node source, String fxmlFile) throws IOException {
        FXMLLoader loader = new FXMLLoader(GoodBurgerApplication.class.getResource(fxmlFile));
        Scene scene = new Scene(loader.load());
        Stage stage = (Stage) source.getScene().getWindow();
        stage.setScene(scene);
    }

    // getter and setter
    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    // Main method to launch the JavaFX application lifecycle
    public static void main(String[] args) {
        launch();
    }
}
