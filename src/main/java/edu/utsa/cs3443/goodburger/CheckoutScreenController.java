package edu.utsa.cs3443.goodburger;

import com.sun.tools.javac.Main;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import model.Order;
import model.Session;

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
        FXMLLoader loader = new FXMLLoader(getClass().getResource("home_page.fxml"));
        try {
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            System.out.println( "Error loading home page");
        }

    }

    @FXML
    void home_button_clicked(ActionEvent event) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("home_page.fxml"));
        try {
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            System.out.println( "Error loading home page");
        }

    }

    @FXML
    void menu_button_clicked(ActionEvent event) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("menu_page.fxml"));
        try {
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            System.out.println( "Error loading menu page");
        }
    }

    @FXML
    void rewards_button_clicked(ActionEvent event) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("rewards_page.fxml"));
        try {
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            System.out.println( "Error loading rewards page");
        }
    }

}
