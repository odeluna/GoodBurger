package edu.utsa.cs3443.goodburger;

/**
 * HomeController manages the home screen, which is the hub that lets the
 * user move to the other screens of the app.
 *
 * author: Anthony Lassiter
 * class: CS3443
 */

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;

import java.io.IOException;

public class HomeScreenController {
    @FXML private ImageView welcomeImage;
    @FXML private Button profileButton;
    @FXML private Button homeButton;
    @FXML private Button menuButton;
    @FXML private Button rewardsButton;
    @FXML private Button checkoutButton;

    // Opens the profile screen
    @FXML
    private void profileClicked(ActionEvent event) {
        goTo(event, "profile_page.fxml");
    }

    // Opens the menu screen
    @FXML
    private void menuClicked(ActionEvent event) {
         goTo(event, "menu_page.fxml");
//        System.out.println("Menu screen is not built yet");
    }

    // Opens the rewards screen
    @FXML
    private void rewardsClicked(ActionEvent event) {
         goTo(event, "rewards_page.fxml");
//        System.out.println("Rewards screen is not built yet");
    }

    // Opens the checkout screen
    @FXML
    private void checkoutClicked(ActionEvent event)  {
         goTo(event, "checkout_page.fxml");
//        System.out.println("Checkout screen is not built yet");
    }

    // Switches to another screen
    private void goTo(ActionEvent event, String fxmlFile) {
        try {
            GoodBurgerApplication.switchScreen((Node) event.getSource(), fxmlFile);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open " + fxmlFile).show();
        }
    }
}