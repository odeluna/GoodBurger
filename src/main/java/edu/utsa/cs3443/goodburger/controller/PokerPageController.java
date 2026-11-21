package edu.utsa.cs3443.goodburger.controller;

import edu.utsa.cs3443.goodburger.GoodBurgerApplication;
import edu.utsa.cs3443.goodburger.model.Session;
import edu.utsa.cs3443.goodburger.model.User;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import rewards.PokerWindow;
import rewards.ProgressStore;

import java.io.IOException;

/**
 * Poker page: the seven-card stud table embedded in the app window so
 * "Play Poker" works like the menu pages (same window, no second popup).
 *
 * <p>The table content comes from the rewards package ({@link
 * PokerWindow#embed}); this page keeps the betting buttons pinned above the
 * nav bar while the table area scrolls if it needs room. The rules view
 * follows the same pattern: Rules replaces the table in place, the pinned row
 * swaps to "Back to poker" / "Back to rewards", and ESC returns to the table.
 * "Back to rewards" (or the rewards nav button) returns to the rewards page,
 * which reloads its points, so both screens agree on the balance.</p>
 *
 * <p>Like the standalone table, points flow through the signed-in user's
 * per-account store: wins and losses land in users.csv on settle.</p>
 *
 * author: Fabricio Ocampo
 * class: CS3443
 */
public class PokerPageController {

    @FXML private ScrollPane contentScroll;
    @FXML private StackPane actionsSlot;

    private ProgressStore progress;
    private PokerWindow.Table table;
    private Node tableRoot;
    private HBox bettingBar;
    private boolean showingRules;

    @FXML
    private void initialize() {
        progress = UserRewardStore.open();
        if (progress == null) {
            new Alert(Alert.AlertType.ERROR, "Could not open the rewards progress file").show();
            return;
        }
        // Same tie-in as the rewards page: the store mirrors this account's
        // row in data/users.csv.
        User user = Session.get().getCurrentUser();
        if (user != null && !user.isGuest()
                && !UserRewardStore.attachToUser(progress)) {
            new Alert(Alert.AlertType.ERROR, "Could not load your points").show();
        }
        // No separate balance UI to refresh: the table renders its own bankroll.
        table = PokerWindow.embed(progress,
                () -> { },
                error -> new Alert(Alert.AlertType.ERROR,
                        "Could not save your poker result").show(),
                this::backToRewards,
                this::showRules);
        tableRoot = table.content();
        bettingBar = table.actionBar();
        contentScroll.setContent(tableRoot);
        actionsSlot.getChildren().add(bettingBar);
        // Leave the table with ESC, the way closing the standalone window did;
        // from the rules view, ESC returns to the table, like closing the popup.
        javafx.application.Platform.runLater(() -> {
            Scene scene = contentScroll.getScene();
            if (scene != null) {
                scene.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        if (showingRules) {
                            backToTable();
                        } else {
                            backToRewards();
                        }
                    }
                });
            }
        });
    }

    /** The rules view replaces the table in the app window; the pinned row
     *  gains "Back to poker" so the hand is still there underneath. */
    private void showRules() {
        if (showingRules) {
            return;
        }
        showingRules = true;
        contentScroll.setContent(table.rulesView());
        actionsSlot.getChildren().clear();
        HBox bar = new HBox(12, pageButton("Back to poker", this::backToTable),
                pageButton("Back to rewards", this::backToRewards));
        bar.setAlignment(javafx.geometry.Pos.CENTER);
        actionsSlot.getChildren().add(bar);
    }

    private void backToTable() {
        if (!showingRules) {
            return;
        }
        showingRules = false;
        contentScroll.setContent(tableRoot);
        actionsSlot.getChildren().clear();
        actionsSlot.getChildren().add(bettingBar);
    }

    private Button pageButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 14 8 14;"
                + " -fx-background-radius: 8;");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void backToRewards() {
        try {
            GoodBurgerApplication.switchScreen(contentScroll, "rewards_page.fxml");
        } catch (IOException error) {
            new Alert(Alert.AlertType.ERROR, "Could not open the rewards page").show();
        }
    }
}
