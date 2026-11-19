package edu.utsa.cs3443.goodburger;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import model.Session;

import java.io.IOException;

public class CheckoutScreenController {

    private Session session = new Session();
    @FXML
    private ListView<MenuItem> order_items;

    @FXML
    void initialize(){
        order_items.getItems().addAll();
    }

    @FXML
    void pay_now_clicked(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Order Submitted");
        alert.setHeaderText("Your order has been submitted!");
        alert.showAndWait();

        session.submitOrder();
        order_items.getItems().clear();
        goTo(event,"home_page.fxml");

    }

    @FXML
    void home_button_clicked(ActionEvent event) {
        goTo(event,"home_page.fxml");

    }

    @FXML
    void menu_button_clicked(ActionEvent event) {
        goTo(event,"menu_page.fxml");
    }

    @FXML
    void rewards_button_clicked(ActionEvent event) {
        goTo(event,"rewards_page.fxml");
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
