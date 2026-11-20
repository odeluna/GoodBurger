package rewards;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** JavaFX rewards interface with the translated Pipe Flight game as its mini-game. */
/**
 * Standalone entry point for the rewards window (own launcher, separate from
 * {@code GoodBurgerApplication}): the Pipe Flight game with its embedded seven-card
 * stud poker, the point economy behind it (ProgressStore) and the reward shop.
 *
 * <p>The main app reaches the same window through {@code RewardsPageController}.
 * The {@code --*--preview} CLI flags render stills of the game screens for
 * verification without a display. See the package-info for the two-codebase layout.</p>
 */
public final class RewardsApp extends Application {
    private static final String BLUE = "#CFE0F1", PEACH = "#FFD3A5", ORANGE = "#FFA100";
    private static final Color INK = Color.web("#30373D");
    private Stage stage;
    private ProgressStore progress;
    private SpriteAtlas sprites;
    private PipeFlightGame previousGame;
    private GameSession session;

    @Override public void start(Stage stage) throws Exception {
        this.stage = stage;
        String path = System.getProperty("rewards.progress.path");
        progress = new ProgressStore(path == null ? ProgressStore.defaultPath() : Path.of(path),
                Clock.systemDefaultZone());
        progress.setGambleDeadlineSupplier(java.time.LocalDate::now);
        progress.onAchievementAwarded(event -> Platform.runLater(() -> showInfo(
                "Achievement unlocked",
                event.definition().name() + ": +" + event.grant() + " points.")));
        sprites = new SpriteAtlas();
        stage.setTitle("Rewards Screen · Pipe Flight");
        stage.setOnCloseRequest(event -> { if (session != null) session.stop(); });
        showPage("Home");
        stage.show();
        List<String> args = getParameters().getRaw();
        if (args.size() == 2 && args.get(0).equals("--game-smoke")) {
            startGame();
            Platform.runLater(() -> {
                try { snapshot(stage.getScene(), Path.of(args.get(1))); }
                catch (IOException error) { error.printStackTrace(); }
                finally { if (session != null) session.stop(); Platform.exit(); }
            });
            return;
        }
        if (args.size() == 3 && args.get(0).equals("--game-preview")) {
            int previewStage = Integer.parseInt(args.get(2));
            PipeFlightGame preview = new PipeFlightGame(new Random(7), sprites);
            preview.failureCount = Math.max(0, Math.min(previewStage, 2));
            preview.playerY = (PipeFlightGame.PLAY_TOP + PipeFlightGame.PLAY_BOTTOM
                    - preview.playerSize()) / 2;
            preview.pipeX = 920;
            preview.sceneTime = 4;
            PipeFlightCanvas canvas = new PipeFlightCanvas(sprites);
            canvas.draw(preview, progress.points(), progress.earnedToday());
            stage.setScene(new Scene(new StackPane(canvas), 1280, 720));
            stage.sizeToScene();
            Platform.runLater(() -> {
                try { snapshot(stage.getScene(), Path.of(args.get(1))); }
                catch (IOException error) { error.printStackTrace(); }
                finally { Platform.exit(); }
            });
            return;
        }
        if (args.size() == 2 && List.of("--poker-preview", "--upgrade-preview",
                "--ending-preview", "--gamble-preview").contains(args.get(0))) {
            PipeFlightGame preview = new PipeFlightGame(new Random(12), sprites);
            switch (args.get(0)) {
                case "--poker-preview" -> {
                    for (int i = 0; i < 8; i++) preview.collected.add(preview.deck.drawTop());
                    preview.beginPoker();
                    preview.pokerAction();
                }
                case "--upgrade-preview" -> {
                    preview.pokerPoints = 1;
                    preview.upgradeMenu = true;
                }
                case "--ending-preview" -> {
                    preview.failureCount = 3;
                    preview.drowning = true;
                    preview.drowningTime = 1.5;
                    preview.playerX = (1280 - 32) / 2.0;
                    preview.playerY = (128 + 640 - 32) / 2.0;
                }
                case "--gamble-preview" -> {
                    preview.failureCount = 3;
                    preview.drowning = true;
                    preview.drowningTime = 1.5;
                    preview.playerX = (1280 - 32) / 2.0;
                    preview.playerY = (128 + 640 - 32) / 2.0;
                    preview.totalPipes = 12;
                    preview.pendingPips = 12;
                    preview.offerGamble();
                }
                default -> { }
            }
            PipeFlightCanvas canvas = new PipeFlightCanvas(sprites);
            canvas.draw(preview, progress.points(), progress.earnedToday());
            stage.setScene(new Scene(new StackPane(canvas), 1280, 720));
            stage.sizeToScene();
            Platform.runLater(() -> {
                try { snapshot(stage.getScene(), Path.of(args.get(1))); }
                catch (IOException error) { error.printStackTrace(); }
                finally { Platform.exit(); }
            });
            return;
        }
        if (args.size() == 2 && args.get(0).equals("--preview")) {
            Platform.runLater(() -> {
                try {
                    snapshot(stage.getScene(), Path.of(args.get(1)));
                } catch (IOException error) {
                    error.printStackTrace();
                } finally {
                    Platform.exit();
                }
            });
        }
    }

    private static void snapshot(Scene scene, Path output) throws IOException {
        javafx.scene.image.WritableImage image = scene.snapshot(null);
        BufferedImage pixels = new BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++)
            for (int x = 0; x < pixels.getWidth(); x++)
                pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        ImageIO.write(pixels, "png", output.toFile());
    }

    private void showPage(String page) {
        try {
            int bonus = progress.claimDailyBonus();
            if (bonus > 0 && stage.getScene() != null) {
                toast("Daily login bonus +" + bonus + " pts - day "
                        + progress.streakDays() + " of your streak");
            }
        } catch (IOException error) { /* the balance still shows once the page renders */ }
        Pane root = shell();
        switch (page) {
            case "Home" -> home(root);
            case "Menu" -> menu(root);
            case "Checkout" -> checkout(root);
            case "Stats" -> stats(root);
            default -> rewards(root);
        }
        navigation(root, page);
        stage.setScene(new Scene(root, 540, 340));
        stage.sizeToScene();
        stage.centerOnScreen();
    }

    private void toast(String message) {
        Label label = new Label(message);
        label.setTextFill(INK);
        label.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        label.setStyle("-fx-background-color: #FFD3A5; -fx-padding: 8; -fx-background-radius: 10;");
        label.setLayoutX((540 - 520) / 2);
        label.setLayoutY(56);
        Pane root = (Pane) stage.getScene().getRoot();
        root.getChildren().add(label);
        javafx.animation.PauseTransition fade = new javafx.animation.PauseTransition(
                javafx.util.Duration.seconds(2.5));
        fade.setOnFinished(event -> root.getChildren().remove(label));
        fade.play();
    }

    private static Pane shell() {
        Pane root = new Pane();
        root.setPrefSize(540, 340);
        root.getChildren().add(box(1, 1, 538, 338, 0, BLUE, 2));
        return root;
    }

    private static Rectangle box(double x, double y, double width, double height,
                                 double radius, String fill, double border) {
        Rectangle rectangle = new Rectangle(x, y, width, height);
        rectangle.setArcWidth(radius);
        rectangle.setArcHeight(radius);
        rectangle.setFill(Color.web(fill));
        rectangle.setStroke(INK);
        rectangle.setStrokeWidth(border);
        return rectangle;
    }

    private static Label label(String value, double x, double y, double width, double height,
                               int size, boolean bold) {
        Label label = new Label(value);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setPrefSize(width, height);
        label.setAlignment(Pos.CENTER);
        label.setFont(Font.font("SansSerif", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        label.setTextFill(INK);
        return label;
    }

    private static Button button(String value, double x, double y, double width, double height,
                                 String fill, int size) {
        Button button = new Button(value);
        button.setLayoutX(x);
        button.setLayoutY(y);
        button.setPrefSize(width, height);
        button.setFont(Font.font("SansSerif", FontWeight.BOLD, size));
        button.setTextFill(INK);
        button.setStyle("-fx-background-color: " + fill + "; -fx-border-color: #30373D; "
                + "-fx-border-width: 2; -fx-border-radius: 9; -fx-background-radius: 9; "
                + "-fx-cursor: hand; -fx-padding: 0;");
        return button;
    }

    private void rewards(Pane root) {
        root.getChildren().add(box(17, 19, 502, 53, 16, BLUE, 2));
        root.getChildren().add(label("Current rewards points : " + progress.points(),
                20, 22, 496, 47, 26, true));
        root.getChildren().add(box(23, 89, 336, 170, 28, BLUE, 2));
        for (int i = 0; i < ProgressStore.REWARD_NAMES.length; i++) {
            int index = i, y = 101 + i * 51;
            root.getChildren().add(box(34, y, 184, 41, 10, BLUE, 2));
            root.getChildren().add(label(ProgressStore.REWARD_NAMES[i].toLowerCase() + ": "
                    + ProgressStore.REWARD_COSTS[i] + " pts", 36, y + 2, 180, 37, 18, true));
            Button redeem = button("Redeem", 230, y, 117, 41, BLUE, 15);
            redeem.setDisable(!progress.canRedeem(i));
            redeem.setTooltip(new Tooltip(redeem.isDisabled()
                    ? "You need " + (ProgressStore.REWARD_COSTS[i] - progress.points()) + " more points"
                    : "Redeem " + ProgressStore.REWARD_NAMES[i]));
            redeem.setOnAction(event -> {
                try {
                    if (progress.redeem(index)) {
                        showPage("Rewards");
                        showInfo("Reward redeemed", ProgressStore.REWARD_NAMES[index]
                                + " is now listed in Checkout. Trade it in there for half its points.");
                    }
                } catch (IOException error) { showError("Could not save the redemption", error); }
            });
            root.getChildren().add(redeem);
        }
        Button miniGame = button("Mini\ngame\n500 pts", 383, 107, 122, 130, BLUE, 18);
        miniGame.setWrapText(true);
        miniGame.setDisable(progress.points() < EconomyRules.PLAY_COST);
        miniGame.setTooltip(new Tooltip("Spend 500 points to play Pipe Flight; earn 1 point per pipe. "
                + "Daily per-pipe earnings cap: 2,000 points. At the end of a run you can double "
                + "or keep its points (one gamble per day)."));
        miniGame.setOnAction(event -> startGame());
        root.getChildren().add(miniGame);
        root.getChildren().add(label("Daily bonus + " + (progress.bonusClaimedToday()
                ? (progress.streakDays() > 0 ? "claimed" : "0") : progress.dailyBonusForToday())
                + " (day " + progress.streakDays() + ")   Earned today: "
                + progress.earnedToday() + " / 2000", 22, 262, 495, 24, 13, false));
    }

    private void home(Pane root) {
        root.getChildren().add(label("Welcome back", 25, 25, 490, 44, 28, true));
        root.getChildren().add(label("Collect points and redeem food rewards.",
                25, 69, 490, 28, 16, false));
        root.getChildren().add(label("Your balance: " + progress.points() + " points",
                70, 107, 400, 30, 22, true));
        root.getChildren().add(label(progress.bonusClaimedToday()
                ? "Login bonus claimed - day " + progress.streakDays()
                        + " of your streak (+100, up to +300)."
                : "Log in daily: +" + progress.dailyBonusForToday()
                        + " pts today (streak days pay more, up to +300).",
                25, 140, 490, 34, 13, false));
        root.getChildren().add(label("Achievements: " + progress.unlockedDefinitions().size()
                + " / " + Achievements.ALL.length + " unlocked", 25, 176, 490, 24, 14, true));
        Button go = button("View rewards", 173, 208, 194, 49, ORANGE, 18);
        go.setOnAction(event -> showPage("Rewards"));
        root.getChildren().add(go);
    }

    private void menu(Pane root) {
        root.getChildren().add(label("Rewards menu", 25, 25, 490, 44, 28, true));
        root.getChildren().add(label("Use your points for one of these items.",
                25, 68, 490, 30, 16, false));
        for (int i = 0; i < ProgressStore.REWARD_NAMES.length; i++)
            root.getChildren().add(label(ProgressStore.REWARD_NAMES[i] + " · "
                    + ProgressStore.REWARD_COSTS[i] + " points", 90, 108 + i * 48, 360, 42, 19, true));
    }

    private void checkout(Pane root) {
        root.getChildren().add(label("Checkout", 25, 23, 490, 44, 28, true));
        root.getChildren().add(label("Trade in an item for half its points.",
                25, 68, 490, 29, 16, false));
        int count = progress.totalRedemptions();
        root.getChildren().add(label(count + (count == 1 ? " reward redeemed" : " rewards redeemed"),
                55, 98, 430, 31, 18, true));
        javafx.scene.layout.VBox list = new javafx.scene.layout.VBox(8);
        list.setStyle("-fx-background-color: #CFE0F1; -fx-padding: 12;");
        if (count == 0) {
            list.getChildren().add(new Label("No rewards redeemed yet."));
        } else {
            for (int i = 0; i < ProgressStore.REWARD_NAMES.length; i++) {
                int qty = progress.redeemedCount(i);
                if (qty == 0) continue;
                final int index = i;
                javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label item = new Label(ProgressStore.REWARD_NAMES[i] + " × " + qty);
                item.setFont(Font.font("SansSerif", FontWeight.BOLD, 15));
                Button tradeIn = new Button("Trade in (+"
                        + (int) progress.tradeBackValue(i) + " pts each)");
                tradeIn.setOnAction(event -> {
                    try {
                        int refund = progress.tradeIn(index);
                        if (refund > 0) {
                            showPage("Checkout");
                            toast("Traded in " + ProgressStore.REWARD_NAMES[index] + " for +"
                                    + refund + " pts");
                        }
                    } catch (IOException error) { showError("Could not save the trade-in", error); }
                });
                row.getChildren().add(item);
                row.getChildren().add(tradeIn);
                list.getChildren().add(row);
            }
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setLayoutX(98); scroll.setLayoutY(136);
        scroll.setPrefSize(344, 135);
        scroll.setFitToWidth(true);
        root.getChildren().add(scroll);
    }

    private void stats(Pane root) {
        root.getChildren().add(label("Your stats", 25, 23, 490, 44, 28, true));
        int achieved = progress.unlockedDefinitions().size();
        root.getChildren().add(label(
                "Best run: " + progress.bestRun() + " pipes    Games played: " + progress.gamesPlayed()
                        + "    Poker wins: " + progress.pokerWins()
                        + "    Redemptions: " + progress.totalRedemptions(),
                25, 74, 490, 40, 15, false));
        javafx.scene.layout.VBox list = new javafx.scene.layout.VBox(6);
        list.setStyle("-fx-background-color: #CFE0F1; -fx-padding: 12;");
        for (Achievements.Definition definition : Achievements.ALL) {
            boolean unlocked = progress.hasUnlocked(definition.id());
            javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label title = new Label((unlocked ? "[OK]  " : "[--]  ")
                    + definition.name() + "  +" + definition.grant() + " pts");
            title.setFont(Font.font("SansSerif", unlocked ? FontWeight.BOLD : FontWeight.NORMAL, 14));
            if (!unlocked) title.setOpacity(.55);
            Label how = new Label(definition.how());
            how.setFont(Font.font("SansSerif", 12));
            how.setOpacity(unlocked ? .7 : .55);
            row.getChildren().add(title);
            row.getChildren().add(how);
            list.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setLayoutX(98); scroll.setLayoutY(118);
        scroll.setPrefSize(344, 155);
        scroll.setFitToWidth(true);
        root.getChildren().add(scroll);
        root.getChildren().add(label(achieved + " / " + Achievements.ALL.length
                + " achievements - one-time bonuses, claimed when you hit the milestone",
                25, 279, 490, 26, 12, false));
    }

    private void navigation(Pane root, String current) {
        String[] names = {"Home", "Menu", "Rewards", "Checkout", "Stats"};
        int[] positions = {2, 109, 206, 303, 400};
        int[] widths = {107, 97, 97, 97, 139};
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            Button nav = button(name.toUpperCase(), positions[i], 291, widths[i], 46,
                    name.equals(current) ? ORANGE : PEACH, 13);
            nav.setOnAction(event -> showPage(name));
            root.getChildren().add(nav);
        }
    }

    private void startGame() {
        try {
            ProgressStore.StartResult result = progress.startPlay();
            if (result == ProgressStore.StartResult.NOT_ENOUGH_POINTS) {
                showInfo("More points needed", "Pipe Flight costs 500 points to start.");
                return;
            }
            if (result == ProgressStore.StartResult.DAILY_CAP_REACHED) {
                showInfo("Daily cap reached", "You have earned 2,000 Pipe Flight points today.");
                return;
            }
            PipeFlightGame game = previousGame == null
                    ? new PipeFlightGame(new Random(), sprites) : previousGame.restartCampaign();
            game.onPokerWin = () -> {
                try {
                    return progress.recordPokerWin();
                } catch (IOException error) {
                    showError("Could not save the poker win", error);
                    return List.of();
                }
            };
            previousGame = game;
            session = new GameSession(game);
            session.open();
        } catch (IOException error) { showError("Could not start Pipe Flight", error); }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, Exception error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(error.getMessage());
        alert.showAndWait();
    }

    private final class GameSession {
        private PipeFlightGame game;
        private final GameAudio audio = new GameAudio();
        private final PipeFlightCanvas canvas = new PipeFlightCanvas(sprites);
        private final Set<KeyCode> pressed = new HashSet<>();
        private boolean flapPending;
        private long previousTime;
        private boolean wasGameOver;
        private ProgressStore.EarnResult lastEarn;
        private final AnimationTimer timer = new AnimationTimer() {
            @Override public void handle(long now) { tick(now); }
        };

        GameSession(PipeFlightGame game) { this.game = game; }

        void open() {
            StackPane root = new StackPane(canvas);
            Scene scene = new Scene(root, PipeFlightGame.WIDTH, PipeFlightGame.HEIGHT, Color.BLACK);
            scene.setOnKeyPressed(event -> keyDown(event.getCode()));
            scene.setOnKeyReleased(event -> pressed.remove(event.getCode()));
            stage.setScene(scene);
            stage.sizeToScene();
            stage.centerOnScreen();
            canvas.draw(game, progress.points(), progress.earnedToday());
            timer.start();
        }

        private void keyDown(KeyCode code) {
            boolean firstPress = pressed.add(code);
            if (!firstPress) return;
            if (code == KeyCode.ESCAPE) { leave(); return; }
            if (code == KeyCode.M) { audio.toggleMute(); return; }
            if (game.gameOver) {
                if (game.canDouble()) {
                    if (code == KeyCode.D) resolveGamble(true);
                    if (code == KeyCode.N) resolveGamble(false);
                    if (code == KeyCode.R || code == KeyCode.ENTER) retry();
                    return;
                }
                if (code == KeyCode.R || code == KeyCode.ENTER) retry();
                return;
            }
            if (game.upgradeMenu) {
                if (code == KeyCode.DIGIT1) game.chooseUpgrade(0);
                if (code == KeyCode.DIGIT2) game.chooseUpgrade(1);
                if (code == KeyCode.DIGIT3) game.chooseUpgrade(2);
                return;
            }
            if (game.poker != null) {
                if (code == KeyCode.ENTER || code == KeyCode.SPACE) game.pokerAction();
                return;
            }
            if (code == KeyCode.W || code == KeyCode.UP || code == KeyCode.SPACE)
                flapPending = true;
        }

        private void resolveGamble(boolean win) {
            try {
                game.resolveGamble(win);
                lastEarn = progress.recordGamble(win, lastEarn);
                toast(win ? "Coin flip won: +" + lastEarn.doubleBonus() + " pts"
                        : "Coin flip lost: this run's points were forfeited");
            } catch (IllegalStateException | IOException error) {
                game.doubled = true;
            }
        }

        private void retry() {
            try {
                ProgressStore.StartResult result = progress.startPlay();
                if (result != ProgressStore.StartResult.STARTED) {
                    showInfo(result == ProgressStore.StartResult.NOT_ENOUGH_POINTS
                            ? "More points needed" : "Daily cap reached",
                            result == ProgressStore.StartResult.NOT_ENOUGH_POINTS
                            ? "Another play costs 500 points." : "The 2,000 point daily cap is reached.");
                    return;
                }
                game = game.restartCampaign();
                previousGame = game;
                flapPending = false;
                pressed.clear();
                previousTime = 0;
                wasGameOver = false;
                lastEarn = null;
            } catch (IOException error) { showError("Could not start another play", error); }
        }

        private void tick(long now) {
            if (previousTime == 0) { previousTime = now; return; }
            double dt = (now - previousTime) / 1_000_000_000.0;
            previousTime = now;
            if (game.drowning) game.advanceDrowning(Math.min(dt, 1.0 / 30));
            else if (!game.gameOver) {
                boolean up = pressed.contains(KeyCode.W) || pressed.contains(KeyCode.UP)
                        || pressed.contains(KeyCode.SPACE);
                boolean down = pressed.contains(KeyCode.S) || pressed.contains(KeyCode.DOWN);
                boolean left = pressed.contains(KeyCode.A) || pressed.contains(KeyCode.LEFT);
                boolean right = pressed.contains(KeyCode.D) || pressed.contains(KeyCode.RIGHT);
                game.update(dt, new PipeFlightGame.Input(flapPending, up, down, left, right));
                flapPending = false;
            }
            if (game.gameOver && !wasGameOver) {
                wasGameOver = true;
                int pipes = game.totalPipes();
                try {
                    lastEarn = progress.finishPlay(pipes);
                    if (lastEarn.baseAward() >= ProgressStore.GAMBLE_MIN_PIPES
                            && progress.gambleOpen()) {
                        game.pendingPips = lastEarn.baseAward();
                        game.offerGamble();
                    }
                } catch (IOException error) {
                    leave();
                    showError("Could not save earned points", error);
                    return;
                }
            }
            audio.update(game);
            canvas.draw(game, progress.points(), progress.earnedToday());
        }

        private void leave() {
            stop();
            session = null;
            previousGame = game;
            showPage("Rewards");
        }

        void stop() { timer.stop(); audio.close(); }
    }

    public static void main(String[] args) { launch(args); }
}
