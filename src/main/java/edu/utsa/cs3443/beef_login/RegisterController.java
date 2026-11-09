package edu.utsa.cs3443.beef_login;
/**
 * RegisterController manages the register screen. It validates the entered
 * name, birthday, email and password, saves the new account to users.csv,
 * and opens the home screen when the account is created.
 *
 * author: Anthony Lassiter
 * class: CS3443
 */

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;

public class RegisterController {
    @FXML private TextField nameInput;
    @FXML private TextField birthdayInput;
    @FXML private TextField emailInput;
    @FXML private PasswordField passwordInput;
    @FXML private PasswordField confirmPasswordInput;
    @FXML private Button submitButton;
    @FXML private Label errorLabel;

    private AccountManager accountManager = new AccountManager();

    // Validates the entered info, creates the account and opens the home screen
    @FXML
    private void submitClicked(ActionEvent event) {
        if (!validateRegistrationFields()) {
            return;
        }

        String name = nameInput.getText().trim();
        String birthday = birthdayInput.getText().trim();
        String email = emailInput.getText().trim();
        String password = passwordInput.getText();

        if (accountManager.findUser(email) != null) {
            showError("An account with that email already exists");
            return;
        }

        if (!accountManager.registerUser(name, birthday, email, password)) {
            showError("Could not create the account");
            return;
        }

        errorLabel.setVisible(false);
        try {
            GoodBurgerApplication.switchScreen((Node) event.getSource(), "home_page.fxml");
        } catch (IOException e) {
            showError("Error loading FXML");
        }
    }

    // Returns false and shows an error if any of the fields are empty or invalid
    private boolean validateRegistrationFields() {
        String name = nameInput.getText().trim();
        String birthday = birthdayInput.getText().trim();
        String email = emailInput.getText().trim();
        String password = passwordInput.getText();
        String confirm = confirmPasswordInput.getText();

        if (name.isEmpty() || birthday.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showError("Please fill in every field");
            return false;
        }
        if (name.contains(",") || password.contains(",")) {
            showError("Name and password cannot contain commas");
            return false;
        }

        // Checks that the birthday is in MM/DD/YYYY format
        if (birthday.length() != 10
                || birthday.charAt(2) != '/'
                || birthday.charAt(5) != '/') {
            showError("Birthday must look like MM/DD/YYYY");
            return false;
        }

        // Checks that the other birthday characters are numbers
        for (int i = 0; i < birthday.length(); i++) {
            if (i != 2 && i != 5 && !Character.isDigit(birthday.charAt(i))) {
                showError("Birthday must look like MM/DD/YYYY");
                return false;
            }
        }

        // Checks that the email has an @ symbol and a period after it
        int atPosition = email.indexOf("@");
        int dotPosition = email.lastIndexOf(".");

        if (atPosition <= 0 || dotPosition <= atPosition + 1
                || dotPosition == email.length() - 1
                || email.contains(",") || email.contains(" ")) {
            showError("Enter a valid email");
            return false;
        }

        if (!password.equals(confirm)) {
            showError("Passwords do not match");
            return false;
        }
        return true;
    }

    // Displays an error message on the register screen
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
