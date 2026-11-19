package rewards;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.InputStream;

/** Optional WAV playback; the game continues normally without an audio device. */
public final class GameAudio implements AutoCloseable {
    private final Clip[] tracks = new Clip[3];
    private final Clip ending;
    private int activeStage = -1;
    private boolean muted;
    private boolean wasGameOver;

    public GameAudio() {
        String[] names = {"rain.wav", "seagulls.wav", "underwater.wav"};
        for (int i = 0; i < tracks.length; i++) tracks[i] = load(names[i]);
        ending = load("game-over.wav");
    }

    private Clip load(String name) {
        try (InputStream source = GameAudio.class.getResourceAsStream("/rewards/assets/audio/" + name)) {
            if (source == null) return null;
            try (var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(source))) {
                Clip clip = AudioSystem.getClip();
                clip.open(audio);
                return clip;
            }
        } catch (Exception unavailable) {
            return null;
        }
    }

    public boolean muted() { return muted; }
    public void toggleMute() { muted = !muted; }

    public void update(PipeFlightGame game) {
        int stage = Math.min(game.stage(), 2);
        if (game.poker != null && game.poker.lost)
            stage = Math.min(Math.max(game.failureCount - 1, 0), 2);
        if (game.gameOver) stage = -1;
        if (stage != activeStage) {
            for (Clip clip : tracks) if (clip != null) clip.stop();
            activeStage = stage;
            if (stage >= 0 && tracks[stage] != null) {
                tracks[stage].setFramePosition(0);
                tracks[stage].loop(Clip.LOOP_CONTINUOUSLY);
            }
        }
        if (stage >= 0) volume(tracks[stage], muted ? 0 :
                (game.poker != null || game.upgradeMenu) ? .2 : .65);
        if (game.gameOver && !wasGameOver && ending != null) {
            ending.setFramePosition(0);
            volume(ending, muted ? 0 : .8);
            ending.start();
        }
        wasGameOver = game.gameOver;
    }

    private void volume(Clip clip, double linear) {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float db = linear <= 0 ? control.getMinimum() : (float) (20 * Math.log10(linear));
        control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), db)));
    }

    @Override public void close() {
        for (Clip clip : tracks) if (clip != null) clip.close();
        if (ending != null) ending.close();
    }
}
