package rewards;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** An eight-card showdown against the dealer, with the best five cards highlighted. */
public final class PokerWindow {
    private PokerWindow() { }

    public static void open(Window owner, ProgressStore progress, Runnable refresh,
                            Consumer<IOException> reportError) {
        new Table(owner, progress, refresh, reportError).show();
    }

    private static final class Table {
        private final Stage stage = new Stage();
        private final HBox dealerCards = new HBox(7);
        private final HBox playerCards = new HBox(7);
        private final Label dealerRank = new Label();
        private final Label playerRank = new Label();
        private final Label result = new Label();
        private final Button reveal = new Button("Reveal showdown");
        private final ProgressStore progress;
        private final Runnable refresh;
        private final Consumer<IOException> reportError;
        private final Random random = new Random();
        private Poker.Round round;
        private boolean revealed;

        Table(Window owner, ProgressStore progress, Runnable refresh, Consumer<IOException> reportError) {
            this.progress = progress;
            this.refresh = refresh;
            this.reportError = reportError;
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle("GoodBurger Poker");

            Label title = new Label("Eight-card poker");
            title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #17434a;");
            Label rules = new Label("Your best five cards play against the dealer's best five. Free to play.");
            Button close = new Button("Back to rewards");
            close.setOnAction(event -> stage.close());
            reveal.setDefaultButton(true);
            reveal.setOnAction(event -> {
                if (revealed) deal();
                else reveal();
            });
            result.setWrapText(true);
            VBox root = new VBox(12, title, rules, new Label("Dealer"), dealerCards, dealerRank,
                    new Label("Your hand"), playerCards, playerRank, result, new HBox(12, reveal, close));
            root.setPadding(new Insets(22));
            root.setStyle("-fx-background-color: #e6f1f3;");
            Scene scene = new Scene(root, 640, 440);
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) stage.close();
            });
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setOnHidden(event -> refresh.run());
            deal();
        }

        void show() { stage.show(); }

        private void deal() {
            round = Poker.Round.deal(random);
            revealed = false;
            renderCards(dealerCards, round.dealerCards(), List.of(), true);
            renderCards(playerCards, round.playerCards(), round.playerBest().cards(), false);
            dealerRank.setText("Hidden until showdown");
            playerRank.setText(round.playerBest().rank().name());
            result.setText("Reveal to compare hands.");
            reveal.setText("Reveal showdown");
        }

        private void reveal() {
            revealed = true;
            renderCards(dealerCards, round.dealerCards(), round.dealerBest().cards(), false);
            dealerRank.setText(round.dealerBest().rank().name());
            int comparison = round.result();
            String outcome = comparison > 0 ? "You win." : comparison < 0 ? "Dealer wins." : "Draw.";
            result.setText(outcome);
            reveal.setText("Deal again");
            try {
                int before = progress.points();
                if (comparison > 0) progress.recordPokerWin();
                progress.claimDailyBonus();
                int awarded = progress.points() - before;
                if (awarded > 0) result.setText(outcome + "  +" + awarded + " rewards points.");
            } catch (IOException error) {
                reportError.accept(error);
            } finally {
                refresh.run();
            }
        }
    }

    private static void renderCards(HBox row, List<Poker.Card> cards, List<Poker.Card> best, boolean hidden) {
        row.getChildren().clear();
        for (Poker.Card card : cards) {
            Label label = new Label(hidden ? "?" : card.value() + " " + card.suitLetter());
            label.setAccessibleText(hidden ? "Hidden card" : card.value() + " of " + card.suit());
            label.setAlignment(javafx.geometry.Pos.CENTER);
            label.setPrefSize(63, 51);
            String color = hidden ? "#ffffff" : card.red() ? "#a91f4c" : "#213542";
            String background = hidden ? "#21626d" : "#ffffff";
            String border = best.contains(card) ? "#d18b33" : "#90aab2";
            label.setStyle("-fx-background-color: " + background + "; -fx-text-fill: " + color
                    + "; -fx-border-color: " + border + "; -fx-border-width: 2; -fx-background-radius: 6;"
                    + " -fx-border-radius: 6; -fx-font-size: 16px; -fx-font-weight: bold;");
            row.getChildren().add(label);
        }
    }
}
