package edu.utsa.cs3443.goodburger;

import edu.utsa.cs3443.goodburger.model.Session;
import edu.utsa.cs3443.goodburger.view.menuPanels.MenuScreen;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;

/**
 * Controller for menu_page.fxml. The FXML only holds the stripes and the shared NavBar; the food menu
 * is the Java-built {@link MenuScreen}, which is dropped into menuSlot here.
 */
public class MenuScreenController {

    @FXML private StackPane menuSlot;

    @FXML
    private void initialize() {
        Session session = Session.get();
        menuSlot.getChildren().add(new MenuScreen(session.getMenuManager(), session.getOrderManager()));
    }
}
