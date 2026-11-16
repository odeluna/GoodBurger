package edu.utsa.cs3443.goodburger;
/**
 * SignInController manages the sign in screen. It validates the entered
 * email and password, checks them against users.csv, and opens the home
 * screen when the sign in succeeds.
 *
 * author: Anthony Lassiter
 * class: CS3443
 */
import edu.utsa.cs3443.goodburger.model.AccountManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;

public class SignInController {
    @FXML private TextField emailInput;
    @FXML private PasswordField passwordInput;
    @FXML private Button signInButton;
    @FXML private Button createAccountButton;
    @FXML private Button guestButton;
    @FXML private Label errorLabel;

    private AccountManager accountManager = new AccountManager();

    // Validates the entered credentials and opens the home screen if they are correct
    @FXML
    private void signInClicked(ActionEvent event) {
        if (!validateInput()) {
            return;
        }

        String email = emailInput.getText().trim();
        String password = passwordInput.getText();

        if (!accountManager.login(email, password)) {
            showError("Wrong email and/or password");
            return;
        }

        errorLabel.setVisible(false);
        goTo(event, "home_page.fxml");
    }

    // Opens the register screen
    @FXML
    private void createAccountClicked(ActionEvent event) {
        goTo(event, "register_page.fxml");
    }

    // Signs in as a guest and opens the home screen
    @FXML
    private void guestClicked(ActionEvent event) {
        accountManager.continueAsGuest();
        goTo(event, "home_page.fxml");
    }

    // Returns false and shows an error if the email or password is empty
    private boolean validateInput() {
        if (emailInput.getText().trim().isEmpty() || passwordInput.getText().isEmpty()) {
            showError("Email and/or password are empty");
            return false;
        }
        return true;
    }

    // Switches to another screen
    private void goTo(ActionEvent event, String fxmlFile) {
        try {
            GoodBurgerApplication.switchScreen((Node) event.getSource(), fxmlFile);
        } catch (IOException e) {
            showError("Error loading FXML");
        }
    }

    // Displays an error message on the sign in screen
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}