package edu.utsa.cs3443.goodburger;

/**
 * ProfileController manages the profile screen. It shows the current user's
 * info, and only shows log out and delete account for signed in users, while
 * guests only see create account.
 *
 * author: Anthony Lassiter
 * class: CS3443
 */

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import model.AccountManager;
import model.User;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class ProfileController implements Initializable {
    @FXML private Label usernameLabel;
    @FXML private Label emailLabel;
    @FXML private Label birthdayLabel;
    @FXML private Label pointsLabel;
    @FXML private Button backButton;
    @FXML private Button logoutButton;
    @FXML private Button deleteAccountButton;
    @FXML private Button createAccountButton;

    private AccountManager accountManager = new AccountManager();

    // Initializes the screen: fills in the user info and shows the right buttons
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        User user = accountManager.getCurrentUser();
        boolean guest = (user == null) || user.isGuest();

        logoutButton.setVisible(!guest);
        deleteAccountButton.setVisible(!guest);
        createAccountButton.setVisible(guest);

        if (guest) {
            usernameLabel.setText("Guest");
            emailLabel.setText("N/A");
            birthdayLabel.setText("N/A");
            pointsLabel.setText("N/A");
        } else {
            usernameLabel.setText(user.getName());
            emailLabel.setText(user.getEmail());
            birthdayLabel.setText(user.getBirthday());
            pointsLabel.setText(String.valueOf(user.getPoints()));
        }
    }

    // Returns to the home screen
    @FXML
    private void backClicked(ActionEvent event) {
        goTo(event, "home_page.fxml");
    }

    // Clears the current user and returns to the sign in screen
    @FXML
    private void logoutClicked(ActionEvent event) {
        accountManager.logout();
        goTo(event, "sign_in_page.fxml");
    }

    // Asks for confirmation, deletes the account and returns to the sign in screen
    @FXML
    private void deleteAccountClicked(ActionEvent event) {

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete your account? This cannot be undone.", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.YES) {
            if (accountManager.deleteAccount(accountManager.getCurrentUser())) {
                goTo(event, "sign_in_page.fxml");
            } else {
                new Alert(Alert.AlertType.ERROR, "Could not delete the account.").show();
            }
        }
    }

    // Guests go to the register screen to make a real account
    @FXML
    private void createAccountClicked(ActionEvent event) {
        goTo(event, "register_page.fxml");
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
