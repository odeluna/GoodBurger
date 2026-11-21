package rewards;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
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
        private static final String WIN_COLOR = "#1e7d46";
        private static final String LOSE_COLOR = "#b3261e";
        private static final String PUSH_COLOR = "#7a6414";
        private static final int CARD_WIDTH = 70;
        private static final int CARD_HEIGHT = 84;

        private final Stage stage = new Stage();
        private final HBox dealerCards = new HBox(7);
        private final HBox playerCards = new HBox(7);
        private final Label roundLabel = new Label();
        private final Label potLabel = new Label();
        private final Label stackLabel = new Label();
        private final Label allInLabel = new Label("ALL-IN");
        private final Label matched = new Label(
                "MATCHED: a shown rank overlaps the dealer's. The pot pushes at showdown.");
        private final Label banner = new Label();
        private final Label detail = new Label();
        private final Button mainAction = new Button("Check");
        private final Button raise = new Button("Raise +" + Poker.Stud.BASE_BET);
        private final Button fold = new Button("Fold");
        private final Button again = new Button("Deal again");
        private final Button rules = new Button("Rules");
        private final Button close = new Button("Back to rewards");
        private final ProgressStore progress;
        private final Runnable refresh;
        private final Consumer<IOException> reportError;
        private final Random random = new Random();
        private Stage rulesStage;
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
            Label blurb = new Label(
                    "Both sides get seven cards: two stay hidden, five are shown, one per round. "
                    + "Best five of seven wins the pot. Press Rules for the full rules and hand rankings.");
            blurb.setWrapText(true);
            blurb.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #5a6b74;");
            for (Label stat : new Label[]{roundLabel, potLabel, stackLabel}) {
                stat.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #213542;");
            }
            allInLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + LOSE_COLOR + ";");
            allInLabel.setVisible(false);
            HBox stats = new HBox(18, roundLabel, potLabel, stackLabel, allInLabel);

            matched.setStyle("-fx-background-color: #fff3d6; -fx-border-color: #d18b33;"
                    + " -fx-border-width: 1; -fx-background-radius: 6; -fx-border-radius: 6;"
                    + " -fx-padding: 5 10 5 10; -fx-text-fill: #8a5a10;"
                    + " -fx-font-weight: bold; -fx-font-size: 12.5px;");
            matched.setVisible(false);
            banner.setVisible(false);
            banner.setManaged(false);
            detail.setWrapText(true);
            detail.setStyle("-fx-font-size: 13.5px; -fx-text-fill: #5a6b74;");
            for (Button button : new Button[]{mainAction, raise, fold, again, rules, close}) {
                button.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 14 8 14;"
                        + " -fx-background-radius: 8;");
            }
            mainAction.setDefaultButton(true);
            mainAction.setOnAction(event -> onMainAction());
            raise.setOnAction(event -> onRaise());
            fold.setOnAction(event -> onFold());
            again.setOnAction(event -> { hand = null; deal(); });
            again.setVisible(false);
            rules.setOnAction(event -> openRules());
            close.setOnAction(event -> stage.close());

            VBox root = new VBox(10, title, blurb,
                    sectionLabel("DEALER"), dealerCards,
                    sectionLabel("YOUR HAND"), playerCards,
                    stats, matched, banner, detail,
                    new HBox(12, mainAction, raise, fold, again, rules, close));
            root.setPadding(new Insets(18));
            root.setStyle("-fx-background-color: #e6f1f3;");
            Scene scene = new Scene(root, 720, 600);
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

        /** The small line under the banner: what each side's best hand was. */
        private String settleText(int net) {
            if (hand.playerRank() == null) {
                return hand.winner() == Poker.Winner.PLAYER
                        ? "Dealer folded before showdown."
                        : "You folded; the dealer takes the pot.";
            }
            String text = "Your hand: " + hand.playerRank().name()
                    + "   vs   Dealer's hand: " + hand.dealerRank().name() + ".";
            if (hand.winner() == Poker.Winner.PUSH) {
                text += hand.isMatched() ? " A shown rank matched, so the pot pushes."
                                         : " Tied hands push the pot.";
            }
            return text;
        }

        private void render() {
            if (hand == null) {
                dealerCards.getChildren().clear();
                playerCards.getChildren().clear();
                roundLabel.setText("");
                potLabel.setText("");
                stackLabel.setText("");
                allInLabel.setVisible(false);
                matched.setVisible(false);
                banner.setVisible(false);
                banner.setManaged(false);
                detail.setText(settleText);
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
            roundLabel.setText("Round " + hand.street() + " of " + Poker.Stud.STREETS);
            potLabel.setText("Pot: " + hand.pot());
            stackLabel.setText("Your stack: " + hand.bankroll());
            allInLabel.setVisible(hand.playerAllIn());
            matched.setVisible(hand.isMatched());
            if (over) {
                setBanner();
                detail.setText(settleText);
            } else {
                banner.setVisible(false);
                banner.setManaged(false);
                detail.setText("Check, raise, or fold. Your best five cards are outlined.");
            }
            mainAction.setText(hand.canCheck() ? "Check" : "Call " + hand.callCost());
            mainAction.setDisable(over);
            raise.setDisable(over || !hand.canRaise());
            fold.setDisable(over);
            again.setVisible(over);
            mainAction.setDefaultButton(!over);
        }

        /** The big verdict: who won and by how many points, in color. */
        private void setBanner() {
            switch (hand.winner()) {
                case PLAYER -> {
                    banner.setText("YOU WIN!  +" + hand.netDelta());
                    banner.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: " + WIN_COLOR + ";");
                }
                case DEALER -> {
                    banner.setText("YOU LOSE  " + hand.netDelta());
                    banner.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: " + LOSE_COLOR + ";");
                }
                case PUSH -> {
                    banner.setText(hand.isMatched() ? "POT PUSHES  (matched shown cards)"
                                                   : "POT PUSHES  (tied hands)");
                    banner.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + PUSH_COLOR + ";");
                }
            }
            banner.setVisible(true);
            banner.setManaged(true);
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

        /** One card: rank over a suit symbol, red for hearts/diamonds, black otherwise. */
        private static Node tile(Poker.Card card, boolean hidden, boolean highlighted) {
            if (hidden) {
                Text mark = new Text("?");
                mark.setFont(Font.font("Bold", 26));
                mark.setFill(Color.web("#ffffff"));
                return cardBox(mark, "#21626d", highlighted);
            }
            Color ink = Color.web(card.red() ? "#b02a4e" : "#1f2d3a");
            Text rank = new Text(card.value());
            rank.setFont(Font.font("Bold", 17));
            rank.setFill(ink);
            Text suit = new Text(suitGlyph(card.suit()));
            suit.setFont(Font.font(20));
            suit.setFill(ink);
            VBox face = new VBox(0, rank, suit);
            face.setAlignment(Pos.CENTER);
            return cardBox(face, "#ffffff", highlighted);
        }

        private static String suitGlyph(String suit) {
            return switch (suit) {
                case "Spades" -> "♠";
                case "Hearts" -> "♥";
                case "Diamonds" -> "♦";
                default -> "♣";
            };
        }

        private static Node cardBox(Node face, String background, boolean highlighted) {
            StackPane box = new StackPane(face);
            box.setAlignment(Pos.CENTER);
            box.setPrefSize(CARD_WIDTH, CARD_HEIGHT);
            box.setStyle("-fx-background-color: " + background
                    + "; -fx-background-radius: 7; -fx-border-color: " + (highlighted ? "#d18b33" : "#90aab2")
                    + "; -fx-border-width: " + (highlighted ? 3 : 2) + "; -fx-border-radius: 7;");
            return box;
        }

        private static Label sectionLabel(String text) {
            Label label = new Label(text);
            label.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #17434a;"
                    + " -fx-letter-spacing: 1px;");
            return label;
        }

        /** Modal popup with the house rules and the full hand-ranking reference. */
        private void openRules() {
            if (rulesStage != null && rulesStage.isShowing()) {
                rulesStage.toFront();
                return;
            }
            rulesStage = buildRulesStage();
            rulesStage.show();
        }

        private Stage buildRulesStage() {
            Stage rules = new Stage();
            rules.initOwner(stage);
            rules.initModality(Modality.WINDOW_MODAL);
            rules.setTitle("Poker rules");
            rules.setResizable(false);

            VBox content = new VBox(8);
            content.setPadding(new Insets(16));
            content.getChildren().add(heading("How to play"));
            content.getChildren().addAll(rulesLine(
                    "You ante 50 points, and the dealer antes 50. Both go into the pot."),
                    rulesLine("Both sides get seven cards: two stay hidden, five are shown, one per round."),
                    rulesLine("Each of the six rounds you can Check (pass), Call (match the bet), "
                            + "Raise (+50), or Fold (give up the pot)."),
                    rulesLine("After the last round the best five of your seven cards is compared "
                            + "to the dealer's. The higher hand wins the pot."),
                    rulesLine("If one of your shown ranks matches one of the dealer's shown ranks, "
                            + "the pot pushes: nobody wins or loses."),
                    rulesLine("Winning a hand also claims your daily bonus."));
            content.getChildren().add(heading("Hand rankings (best to worst)"));
            content.getChildren().addAll(rankingLine("Straight flush",
                            "Five cards in sequence, all the same suit. Example: A♠ K♠ Q♠ J♠ 10♠"),
                    rankingLine("Four of a kind",
                            "Four cards of the same rank. Example: K♥ K♦ K♣ K♠ 3♣"),
                    rankingLine("Full house",
                            "Three of a kind plus a pair. Example: Q♥ Q♣ Q♦ 7♠ 7♥"),
                    rankingLine("Flush",
                            "Five cards of the same suit, not in sequence. Example: 9♥ 7♥ 5♥ 3♥ 2♥"),
                    rankingLine("Straight",
                            "Five cards in sequence, mixed suits. Example: 9♠ 8♥ 7♦ 6♣ 5♠"),
                    rankingLine("Three of a kind",
                            "Three cards of the same rank. Example: J♠ J♥ J♦ 9♣ 4♠"),
                    rankingLine("Two pair",
                            "Two different pairs. Example: 10♠ 10♥ 4♣ 4♦ K♠"),
                    rankingLine("One pair",
                            "Two cards of the same rank. Example: A♠ A♥ 9♠ 6♦ 2♣"),
                    rankingLine("High card",
                            "No combination. The highest card decides. Example: K♠ 9♥ 7♦ 4♣ 2♠"));
            Label note = new Label("An ace can be high or low: A-2-3-4-5 is the lowest straight. "
                    + "Tied hands push the pot.");
            note.setWrapText(true);
            note.setStyle("-fx-font-size: 12px; -fx-text-fill: #5a6b74;");
            content.getChildren().add(note);

            ScrollPane scroll = new ScrollPane(content);
            scroll.setFitToWidth(true);
            Button closeRules = new Button("Close");
            closeRules.setOnAction(event -> rules.close());
            HBox bar = new HBox(closeRules);
            bar.setAlignment(Pos.CENTER_RIGHT);
            bar.setPadding(new Insets(10));
            VBox root = new VBox(scroll, bar);
            root.setStyle("-fx-background-color: #e6f1f3;");
            Scene scene = new Scene(root, 500, 560);
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) rules.close();
            });
            rules.setScene(scene);
            return rules;
        }

        private static Label heading(String text) {
            Label label = new Label(text);
            label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #17434a;");
            return label;
        }

        private static Label rulesLine(String text) {
            Label label = new Label("•  " + text);
            label.setWrapText(true);
            label.setStyle("-fx-font-size: 13px; -fx-text-fill: #213542;");
            return label;
        }

        private static VBox rankingLine(String name, String example) {
            Label title = new Label(name);
            title.setStyle("-fx-font-size: 13.5px; -fx-font-weight: bold; -fx-text-fill: #213542;");
            Label exampleLabel = new Label(example);
            exampleLabel.setWrapText(true);
            exampleLabel.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #5a6b74;");
            VBox box = new VBox(1, title, exampleLabel);
            return box;
        }
    }
}
