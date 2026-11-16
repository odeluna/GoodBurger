package edu.utsa.cs3443.goodburger;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import rewards.EconomyRules;
import rewards.PipeFlightGame;
import rewards.ProgressStore;
import rewards.RewardGameDriver;
import rewards.SpriteAtlas;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Random;

/**
 * RewardsPageController is the rewards screen of the app (the page the
 * home and checkout "rewards" buttons open). It shows the point balance,
 * the redeemable food rewards, and the Pipe Flight mini game launcher.
 *
 * All point logic lives in the rewards package (ProgressStore and friends),
 * so the page and the mini game share the same economy and the same saved
 * progress file as the standalone RewardsApp.
 *
 * author: Fabricio Ocampo
 * class: CS3443
 */
public class RewardsPageController {

    @FXML private Label pointsLabel;
    @FXML private Label reward1Name;
    @FXML private Label reward2Name;
    @FXML private Label reward3Name;
    @FXML private Button redeem1Button;
    @FXML private Button redeem2Button;
    @FXML private Button redeem3Button;
    @FXML private Button miniGameButton;
    @FXML private Label miniGameStatsLabel;
    @FXML private Button homeButton;
    @FXML private Button menuButton;
    @FXML private Button rewardsNavButton;
    @FXML private Button checkoutButton;

    private ProgressStore progress;
    private SpriteAtlas sprites;
    private PipeFlightGame previousGame;
    private RewardGameDriver activeDriver;

    @FXML
    private void initialize() {
        String path = System.getProperty("rewards.progress.path");
        try {
            progress = new ProgressStore(path == null ? ProgressStore.defaultPath() : Path.of(path),
                    Clock.systemDefaultZone());
            progress.setGambleDeadlineSupplier(java.time.LocalDate::now);
            progress.onAchievementAwarded(event -> Platform.runLater(() ->
                    toast("Achievement unlocked: " + event.definition().name()
                            + " (+" + event.grant() + " pts)")));
        } catch (IOException error) {
            // Keep the page usable even if the progress file cannot be read:
            // fall back to a throwaway location so the controls still render.
            try {
                progress = new ProgressStore(
                        java.nio.file.Files.createTempDirectory("rewards-page").resolve("progress.properties"),
                        Clock.systemDefaultZone());
            } catch (IOException fallback) {
                new Alert(Alert.AlertType.ERROR,
                        "Could not open the rewards progress file: " + error.getMessage()).show();
            }
        }
        if (progress != null) {
            try {
                sprites = new SpriteAtlas();
            } catch (IOException error) {
                // The game reports the missing sprites if it is started.
            }
        }
        refreshData();
    }

    // Refreshes every label and button state from the progress store.
    private void refreshData() {
        if (progress == null) return;
        pointsLabel.setText("Current rewards points: " + progress.points());
        Button[] buttons = {redeem1Button, redeem2Button, redeem3Button};
        for (int i = 0; i < ProgressStore.REWARD_NAMES.length; i++) {
            buttons[i].setDisable(!progress.canRedeem(i));
            buttons[i].setTooltip(new Tooltip(progress.canRedeem(i)
                    ? "Redeem " + ProgressStore.REWARD_NAMES[i]
                    : "You need " + (ProgressStore.REWARD_COSTS[i] - progress.points()) + " more points"));
        }
        reward1Name.setText(ProgressStore.REWARD_NAMES[0] + ": " + ProgressStore.REWARD_COSTS[0] + " pts");
        reward2Name.setText(ProgressStore.REWARD_NAMES[1] + ": " + ProgressStore.REWARD_COSTS[1] + " pts");
        reward3Name.setText(ProgressStore.REWARD_NAMES[2] + ": " + ProgressStore.REWARD_COSTS[2] + " pts");
        boolean enough = progress.points() >= EconomyRules.PLAY_COST;
        miniGameButton.setDisable(!enough);
        miniGameButton.setTooltip(new Tooltip(
                "Spend " + EconomyRules.PLAY_COST + " points to play Pipe Flight; earn 1 point per pipe. "
                        + "Daily per-pipe earnings cap: " + EconomyRules.DAILY_EARNINGS_CAP + " points."));
        miniGameStatsLabel.setText("Daily bonus + "
                + (progress.bonusClaimedToday()
                ? (progress.streakDays() > 0 ? "claimed" : "0")
                : progress.dailyBonusForToday())
                + " (day " + progress.streakDays() + ")\nEarned today: "
                + progress.earnedToday() + " / " + EconomyRules.DAILY_EARNINGS_CAP);
    }

    // Redeems one reward; the item then shows up in Checkout to trade in.
    private void redeem(int index) {
        try {
            if (progress.redeem(index)) {
                refreshData();
                showInfo("Reward redeemed", ProgressStore.REWARD_NAMES[index]
                        + " is now listed in Checkout. Trade it in there for half its points.");
            } else {
                showInfo("Not enough points", "You need "
                        + (ProgressStore.REWARD_COSTS[index] - progress.points())
                        + " more points to redeem " + ProgressStore.REWARD_NAMES[index] + ".");
            }
        } catch (IOException error) {
            showError("Could not save the redemption", error);
        }
    }

    @FXML private void redeem1Clicked(ActionEvent event) { redeem(0); }
    @FXML private void redeem2Clicked(ActionEvent event) { redeem(1); }
    @FXML private void redeem3Clicked(ActionEvent event) { redeem(2); }

    // Starts Pipe Flight in its own window, like the standalone RewardsApp.
    @FXML
    private void miniGameClicked(ActionEvent event) {
        if (activeDriver != null) return; // one game window at a time
        try {
            ProgressStore.StartResult result = progress.startPlay();
            if (result == ProgressStore.StartResult.NOT_ENOUGH_POINTS) {
                showInfo("More points needed",
                        "Pipe Flight costs " + EconomyRules.PLAY_COST + " points to start.");
                return;
            }
            if (result == ProgressStore.StartResult.DAILY_CAP_REACHED) {
                showInfo("Daily cap reached", "You have earned "
                        + EconomyRules.DAILY_EARNINGS_CAP + " Pipe Flight points today.");
                return;
            }
            PipeFlightGame game = previousGame == null
                    ? new PipeFlightGame(new Random(), sprites) : previousGame.restartCampaign();
            previousGame = game;
            game.onPokerWin = () -> {
                try {
                    return progress.recordPokerWin();
                } catch (IOException error) {
                    showError("Could not save the poker win", error);
                    return List.of();
                }
            };
            Stage gameStage = new Stage();
            activeDriver = new RewardGameDriver(game, sprites, progress, this::afterGame);
            activeDriver.open(gameStage);
            gameStage.setOnCloseRequest(event2 -> endGame());
            refreshData();
        } catch (IOException error) {
            showError("Could not start Pipe Flight", error);
        }
    }

    // Called by the driver when the player leaves the game with Esc.
    private void afterGame() {
        endGame();
    }

    private void endGame() {
        if (activeDriver != null) {
            activeDriver.stop();
            activeDriver = null;
        }
        Platform.runLater(this::refreshData);
    }

    @FXML private void homeButtonClicked(ActionEvent event) { goTo(event, "home_page.fxml"); }
    @FXML private void menuButtonClicked(ActionEvent event) {
        new Alert(Alert.AlertType.INFORMATION, "Menu screen is not built yet").show();
    }
    @FXML private void checkoutButtonClicked(ActionEvent event) { goTo(event, "checkout_page.fxml"); }

    // Switches to another screen (same pattern as the other controllers).
    private void goTo(ActionEvent event, String fxmlFile) {
        try {
            GoodBurgerApplication.switchScreen((Node) event.getSource(), fxmlFile);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open " + fxmlFile).show();
        }
    }

    // Small themed notification at the top of the page (auto-fades).
    private void toast(String message) {
        Label label = new Label(message);
        label.setTextFill(Color.web("#30373D"));
        label.setStyle("-fx-background-color: #FFD3A5; -fx-padding: 8; -fx-background-radius: 10; "
                + "-fx-font-weight: bold; -fx-font-size: 13;");
        label.setLayoutX(40);
        label.setLayoutY(56);
        Pane root = (Pane) homeButton.getScene().getRoot();
        if (root == null) return;
        root.getChildren().add(label);
        PauseTransition fade = new PauseTransition(javafx.util.Duration.seconds(2.5));
        fade.setOnFinished(event -> root.getChildren().remove(label));
        fade.play();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    private void showError(String title, Exception error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(error.getMessage());
        alert.show();
    }
}