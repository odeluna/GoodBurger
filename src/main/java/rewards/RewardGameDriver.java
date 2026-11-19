package rewards;

import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drives one Pipe Flight run (input, per-frame updates, canvas drawing and
 * audio) inside a standalone {@link Stage} so an embedded rewards page can
 * open the game in its own window without pulling game internals into the
 * UI package. The driver owns the game loop; the page owns the window.
 */
public final class RewardGameDriver {
    /** Called on the FX thread when the player leaves the game (Esc). */
    public interface OnLeave { void onLeave(); }

    private PipeFlightGame game;
    private final ProgressStore progress;
    private final OnLeave onLeave;
    private final GameAudio audio = new GameAudio();
    private final PipeFlightCanvas canvas;
    private final Set<KeyCode> pressed = new HashSet<>();
    private boolean flapPending;
    private long previousTime;
    private boolean wasGameOver;
    private ProgressStore.EarnResult lastEarn;
    private final AnimationTimer timer = new AnimationTimer() {
        @Override public void handle(long now) { tick(now); }
    };

    /** @param game the run to drive (fresh or a restarted campaign) */
    public RewardGameDriver(PipeFlightGame game, SpriteAtlas sprites,
                            ProgressStore progress, OnLeave onLeave) {
        this.game = game;
        this.sprites = sprites;
        this.progress = progress;
        this.onLeave = onLeave;
        this.canvas = new PipeFlightCanvas(sprites);
    }

    private final SpriteAtlas sprites;

    /** Shows the game window and starts the loop. */
    public void open(Stage stage) {
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, PipeFlightGame.WIDTH, PipeFlightGame.HEIGHT, Color.BLACK);
        scene.setOnKeyPressed(event -> keyDown(event.getCode()));
        scene.setOnKeyReleased(event -> pressed.remove(event.getCode()));
        stage.setTitle("Pipe Flight");
        stage.setScene(scene);
        stage.sizeToScene();
        stage.centerOnScreen();
        canvas.draw(game, progress.points(), progress.earnedToday());
        timer.start();
        stage.show();
    }

    /** The run currently being driven (may be a restarted campaign). */
    public PipeFlightGame current() { return game; }

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
        } catch (IllegalStateException | IOException error) {
            game.doubled = true;
        }
    }

    private void retry() {
        try {
            ProgressStore.StartResult result = progress.startPlay();
            if (result != ProgressStore.StartResult.STARTED) return;
            game = game.restartCampaign();
            flapPending = false;
            pressed.clear();
            previousTime = 0;
            wasGameOver = false;
            lastEarn = null;
        } catch (IOException error) {
            // Points are short for a retry; the page will show the balance next render.
        }
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
                return;
            }
        }
        audio.update(game);
        canvas.draw(game, progress.points(), progress.earnedToday());
    }

    private void leave() {
        stop();
        if (onLeave != null) onLeave.onLeave();
    }

    /** Stops the loop and closes the audio (call when the window closes). */
    public void stop() { timer.stop(); audio.close(); }
}
