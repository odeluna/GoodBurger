package rewards;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Seven-card stud table: two hidden cards, five shown, six betting streets. */
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
        private final Label status = new Label();
        private final Label matched = new Label("MATCHED: a shown rank overlaps the dealer's. The pot pushes at showdown.");
        private final Label result = new Label();
        private final Button mainAction = new Button("Check");
        private final Button raise = new Button("Raise +" + Poker.Stud.BASE_BET);
        private final Button fold = new Button("Fold");
        private final Button again = new Button("Deal again");
        private final Button close = new Button("Back to rewards");
        private final ProgressStore progress;
        private final Runnable refresh;
        private final Consumer<IOException> reportError;
        private final Random random = new Random();
        private Poker.Stud hand;
        private String settleText = "";

        Table(Window owner, ProgressStore progress, Runnable refresh, Consumer<IOException> reportError) {
            this.progress = progress;
            this.refresh = refresh;
            this.reportError = reportError;
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle("GoodBurger Poker - Seven-card Stud");

            Label title = new Label("Seven-card stud");
            title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #17434a;");
            Label rules = new Label("Two hidden cards, five shown. Six betting streets; best five of seven wins the pot.\n"
                    + "You act first on every street. Shown cards that match the dealer's push the pot.");
            matched.setStyle("-fx-text-fill: #a91f4c; -fx-font-weight: bold;");
            matched.setVisible(false);
            result.setWrapText(true);
            mainAction.setDefaultButton(true);
            mainAction.setOnAction(event -> onMainAction());
            raise.setOnAction(event -> onRaise());
            fold.setOnAction(event -> onFold());
            again.setOnAction(event -> { hand = null; deal(); });
            again.setVisible(false);
            close.setOnAction(event -> stage.close());

            VBox root = new VBox(12, title, rules,
                    new Label("Dealer"), dealerCards,
                    new Label("Your hand"), playerCards,
                    status, matched, result,
                    new HBox(12, mainAction, raise, fold, again, close));
            root.setPadding(new Insets(22));
            root.setStyle("-fx-background-color: #e6f1f3;");
            Scene scene = new Scene(root, 720, 480);
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
            try {
                hand = Poker.Stud.deal(random, progress.points());
                settleText = "";
            } catch (IllegalArgumentException error) {
                settleText = "You need at least " + Poker.Stud.ANTE + " points to ante for a new hand.";
                hand = null;
            }
            render();
        }

        private void onMainAction() {
            if (hand == null || hand.isOver()) return;
            if (hand.canCheck()) hand.playerCheck();
            else hand.playerCall();
            afterAction();
        }

        private void onRaise() {
            if (hand == null || hand.isOver() || !hand.canRaise()) return;
            hand.playerRaise();
            afterAction();
        }

        private void onFold() {
            if (hand == null || hand.isOver()) return;
            hand.playerFold();
            afterAction();
        }

        private void afterAction() {
            if (hand.isOver()) settle();
            render();
        }

        /** Applies the net points once, then records the win and daily bonus on a profit. */
        private void settle() {
            int net = hand.netDelta();
            try {
                progress.adjustPoints(net);
                if (net > 0) {
                    progress.recordPokerWin();
                    progress.claimDailyBonus();
                }
            } catch (IOException error) {
                reportError.accept(error);
            } finally {
                refresh.run();
            }
            settleText = settleText(net);
        }

        private String settleText(int net) {
            String text;
            if (hand.playerRank() == null) {
                text = hand.winner() == Poker.Winner.PLAYER
                        ? "Dealer folded. You win the pot."
                        : "You folded. Dealer wins the pot.";
            } else {
                text = "You: " + hand.playerRank().name() + "   vs   Dealer: " + hand.dealerRank().name() + ". ";
                text += switch (hand.winner()) {
                    case PLAYER -> "You win the pot.";
                    case DEALER -> "Dealer wins the pot.";
                    case PUSH -> hand.isMatched() ? "Matched shown cards push the pot."
                            : "Tied hands push the pot.";
                };
            }
            if (net > 0) text += "  +" + net + " points.";
            else if (net < 0) text += "  " + net + " points.";
            return text;
        }

        private void render() {
            if (hand == null) {
                dealerCards.getChildren().clear();
                playerCards.getChildren().clear();
                status.setText("");
                matched.setVisible(false);
                result.setText(settleText);
                mainAction.setDisable(true);
                raise.setDisable(true);
                fold.setDisable(true);
                again.setVisible(true);
                return;
            }
            boolean over = hand.isOver();
            renderRow(dealerCards, hand.dealerHole(), !over,
                    hand.dealerUpRevealed(), 5 - hand.dealerUpRevealed().size(),
                    over ? bestFiveCards(hand.dealerCards()) : List.of());
            List<Poker.Card> playerVisible = new ArrayList<>(hand.playerHole());
            playerVisible.addAll(hand.playerUpRevealed());
            renderRow(playerCards, hand.playerHole(), false,
                    hand.playerUpRevealed(), 5 - hand.playerUpRevealed().size(),
                    playerVisible.size() >= 5 ? bestFiveCards(playerVisible)
                            : over ? List.of() : List.of());
            status.setText("Street " + hand.street() + " of " + Poker.Stud.STREETS
                    + "   Pot: " + hand.pot()
                    + "   Your stack: " + hand.bankroll()
                    + (hand.playerAllIn() ? "   ALL-IN" : ""));
            matched.setVisible(hand.isMatched());
            result.setText(over ? settleText : "Check, raise, or fold. Your best five are outlined.");
            mainAction.setText(hand.canCheck() ? "Check" : "Call " + hand.callCost());
            mainAction.setDisable(over);
            raise.setDisable(over || !hand.canRaise());
            fold.setDisable(over);
            again.setVisible(over);
            mainAction.setDefaultButton(!over);
        }

        private static List<Poker.Card> bestFiveCards(List<Poker.Card> cards) {
            if (cards.size() < 5) return List.of();
            return Poker.bestFive(cards).cards();
        }

        private static void renderRow(HBox row, List<Poker.Card> hole, boolean holeHidden,
                                      List<Poker.Card> upShown, int upHidden, List<Poker.Card> highlight) {
            row.getChildren().clear();
            for (Poker.Card card : hole) row.getChildren().add(tile(card, holeHidden, highlight.contains(card)));
            for (Poker.Card card : upShown) row.getChildren().add(tile(card, false, highlight.contains(card)));
            for (int i = 0; i < upHidden; i++) row.getChildren().add(tile(null, true, false));
        }

        private static Label tile(Poker.Card card, boolean hidden, boolean highlighted) {
            Label label = new Label(hidden ? "?" : card.value() + " " + card.suitLetter());
            label.setAlignment(Pos.CENTER);
            label.setPrefSize(72, 56);
            String color = hidden ? "#ffffff" : card.red() ? "#a91f4c" : "#213542";
            String background = hidden ? "#21626d" : "#ffffff";
            String border = highlighted ? "#d18b33" : "#90aab2";
            label.setStyle("-fx-background-color: " + background + "; -fx-text-fill: " + color
                    + "; -fx-border-color: " + border + "; -fx-border-width: 2; -fx-background-radius: 6;"
                    + " -fx-border-radius: 6; -fx-font-size: 17px; -fx-font-weight: bold;");
            return label;
        }
    }
}
