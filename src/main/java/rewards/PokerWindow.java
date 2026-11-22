package rewards;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import javafx.application.Platform;
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

/**
 * Seven-card stud table: two hidden cards, five shown, six betting streets.
 *
 * <p>The table can be shown as a standalone modal window ({@link #open}) or
 * embedded inside an app page ({@link #embed}): same cards, betting, and rules
 * in both forms.</p>
 */
public final class PokerWindow {
    private PokerWindow() { }

    /** Opens the table as a standalone modal window owned by {@code owner}. */
    public static void open(Window owner, ProgressStore progress, Runnable refresh,
                            Consumer<IOException> reportError) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        new Table(stage, progress, refresh, reportError, stage::close, null).show();
    }

    /**
     * The table as an in-app panel for a page to host: {@link #content()} is
     * the table area and {@link #actionBar()} is the betting button row, so a
     * page can pin the buttons while the table scrolls. {@code onClose} fires
     * when the "Back to rewards" button is pressed; {@code onRules} fires when
     * Rules is pressed, so a page can show {@link #rulesView()} in place of the
     * table instead of opening a popup.
     */
    public static Table embed(ProgressStore progress, Runnable refresh,
                              Consumer<IOException> reportError, Runnable onClose,
                              Runnable onRules) {
        return new Table(null, progress, refresh, reportError, onClose, onRules);
    }

    public static final class Table {
        private static final String WIN_COLOR = "#1e7d46";
        private static final String LOSE_COLOR = "#b3261e";
        private static final String PUSH_COLOR = "#7a6414";
        private static final int CARD_WIDTH = 70;
        private static final int CARD_HEIGHT = 84;

        /** The standalone window; null when the table is embedded in a page. */
        private final Stage stage;
        private final VBox root;
        private final HBox actionBar;
        private final Runnable onClose;
        private final Runnable onRules;
        private final HBox dealerCards = new HBox(7);
        private final HBox playerCards = new HBox(7);
        private final Label roundLabel = new Label();
        private final Label potLabel = new Label();
        private final Label stackLabel = new Label();
        private final Label allInLabel = new Label("ALL-IN");
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
        private VBox rulesContent;
        private Poker.Stud hand;
        private String settleText = "";
        private boolean wasOver;

        Table(Stage stage, ProgressStore progress, Runnable refresh, Consumer<IOException> reportError,
              Runnable onClose, Runnable onRules) {
            this.stage = stage;
            this.progress = progress;
            this.refresh = refresh;
            this.reportError = reportError;
            this.onClose = onClose;
            this.onRules = onRules;
            if (stage != null) {
                stage.initModality(Modality.WINDOW_MODAL);
                stage.setTitle("GoodBurger Poker - Seven-card Stud");
            }

            Label title = new Label("Seven-card stud");
            title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #17434a;");
            Label blurb = new Label(
                    "Both sides get seven cards: two stay hidden, five are shown, one per round. "
                    + "Best five of seven wins the pot. Press Rules for the full rules and hand rankings.");
            blurb.setWrapText(true);
            blurb.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #5a6b74;");
            if (stage == null) {
                // Embedded page: the pinned Rules button plus the one-line detail
                // cover the basics, so drop the blurb to keep the table compact.
                blurb.setVisible(false);
            }
            for (Label stat : new Label[]{roundLabel, potLabel, stackLabel}) {
                stat.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #213542;");
            }
            allInLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + LOSE_COLOR + ";");
            allInLabel.setVisible(false);
            HBox stats = new HBox(18, roundLabel, potLabel, stackLabel, allInLabel);

            banner.setVisible(false);
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
            again.setOnAction(event -> {
                hand = null;
                deal();
                scrollToTop();
            });
            again.setVisible(false);
            rules.setOnAction(event -> openRules());
            close.setOnAction(event -> onClose.run());

            Node[] body = { title, blurb,
                    sectionLabel("DEALER"), dealerCards,
                    sectionLabel("YOUR HAND"), playerCards,
                    stats, banner, detail };
            this.actionBar = new HBox(12, mainAction, raise, fold, again, rules, close);
            this.root = new VBox(10);
            for (Node node : body) {
                root.getChildren().add(node);
            }
            if (stage != null) {
                root.getChildren().add(actionBar);
            }
            root.setPadding(new Insets(18));
            root.setStyle("-fx-background-color: #e6f1f3;");
            if (stage != null) {
                Scene scene = new Scene(root, 720, 600);
                scene.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ESCAPE) onClose.run();
                });
                stage.setScene(scene);
                stage.setResizable(false);
                stage.setOnHidden(event -> refresh.run());
            }
            deal();
        }

        /** The table area for an in-app page (without the betting bar). */
        public Node content() {
            return root;
        }

        /** The betting button row; a page pins it outside the scrolling area. */
        public HBox actionBar() {
            return actionBar;
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

        /** The small line under the banner: how the hand ended and each side's best hand. */
        private String settleText(int net) {
            String text;
            if (hand.folded()) {
                text = "You folded; the hand was run out so every card is shown. You lose half the stake.";
            } else {
                text = "Your hand: " + hand.playerRank().name()
                        + "   vs   Dealer's hand: " + hand.dealerRank().name() + ".";
                if (hand.winner() == Poker.Winner.PUSH) {
                    text += " Tied hands push the pot.";
                }
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
            if (over && !wasOver) scrollToShowSettle();
            wasOver = over;
            renderRow(dealerCards,
                    over ? List.copyOf(hand.dealerHole()) : List.of(),
                    over ? List.of() : hand.dealerHole(),
                    hand.dealerUpRevealed(), 5 - hand.dealerUpRevealed().size(),
                    over ? bestFiveCards(hand.dealerCards()) : List.of());
            List<Poker.Card> holeShown = over ? hand.playerHole() : hand.playerHoleRevealed();
            List<Poker.Card> holeHidden = hand.playerHole().subList(holeShown.size(), Poker.Stud.HOLE_CARDS);
            List<Poker.Card> playerVisible = new ArrayList<>(holeShown);
            playerVisible.addAll(hand.playerUpRevealed());
            renderRow(playerCards, holeShown, List.copyOf(holeHidden),
                    hand.playerUpRevealed(), 5 - hand.playerUpRevealed().size(),
                    playerVisible.size() >= 5 ? bestFiveCards(playerVisible)
                            : over ? List.of() : List.of());
            roundLabel.setText("Round " + hand.street() + " of " + Poker.Stud.STREETS);
            potLabel.setText("Pot: " + hand.pot());
            stackLabel.setText("Your stack: " + hand.bankroll());
            allInLabel.setVisible(hand.playerAllIn());
            if (over) {
                setBanner();
                detail.setText(settleText);
            } else if (!hand.canCheck()) {
                detail.setText("Your two checks are used up for this hand: raise, or fold.");
            } else {
                banner.setVisible(false);
                banner.setManaged(false);
                detail.setText("You can check at most twice per hand. Check or raise reveals a hole card. "
                        + "Your best five cards are outlined.");
            }
            mainAction.setText("Check (" + hand.checksLeft() + " left)");
            mainAction.setDisable(over || !hand.canCheck());
            raise.setText("Raise +" + (hand.canRaise() ? hand.raiseCost() : Poker.Stud.BASE_BET));
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
                    banner.setText((hand.folded() ? "YOU FOLDED  " : "YOU LOSE  ") + hand.netDelta());
                    banner.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: " + LOSE_COLOR + ";");
                }
                case PUSH -> {
                    banner.setText("POT PUSHES  (tied hands)");
                    banner.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + PUSH_COLOR + ";");
                }
            }
            banner.setVisible(true);
            banner.setManaged(true);
        }

        /**
         * When the table is embedded in a scrolling page, the settle banner lands
         * at the bottom of the table: bring it into the viewport once the hand
         * ends, and back to the top when a fresh hand is dealt.
         */
        private void scrollToShowSettle() { scrollBannerIntoView(0, -1); }

        private void scrollToTop() { scrollToValue(0); }

        private void scrollToValue(double value) {
            Node node = root;
            while (node != null) {
                if (node instanceof ScrollPane pane) {
                    // Defer a full pulse so the banner's layout change has been
                    // applied before the scroll range is read.
                    Platform.runLater(() -> Platform.runLater(() -> pane.setVvalue(value)));
                    return;
                }
                node = node.getParent();
            }
        }

        /**
         * Scrolls so the banner's bottom edge sits just above the viewport
         * bottom. ScrollPane preserves the pixel offset behind a vvalue, so
         * the value may only be written once the content has reached its
         * final height: the geometry is re-measured each pulse until the
         * banner reports a real height AND its bottom edge has stopped
         * moving (max 64 pulses).
         */
        private void scrollBannerIntoView(int attempt, double lastBottom) {
            Node node = root;
            while (node != null && !(node instanceof ScrollPane)) {
                node = node.getParent();
            }
            if (!(node instanceof ScrollPane pane)) return;
            Platform.runLater(() -> {
                double bannerH = banner.getLayoutBounds().getHeight();
                double contentH = root.getLayoutBounds().getHeight();
                double viewportH = pane.getViewportBounds().getHeight();
                double range = contentH - viewportH;
                double bottom = bannerH > 1
                        ? banner.localToScene(0, bannerH).getY() - root.localToScene(0, 0).getY()
                        : contentH;
                if (bannerH <= 1 || range <= 1) {
                    if (attempt < 64) {
                        final double b = bottom;
                        Platform.runLater(() -> scrollBannerIntoView(attempt + 1, b));
                    }
                    return;
                }
                double target = (bottom - viewportH + 8) / range;
                double v = Math.max(0, Math.min(1, target));
                double top = v * range;
                boolean inView = bottom - 8 <= top + viewportH && bottom >= top;
                if (inView && bottom == lastBottom) return;
                // Rows above the banner (revealed dealer cards, best-five
                // outlines) can still shift it for pulses after the settle, so
                // re-aim on every pulse until the bottom edge is both in view
                // and stopped moving.
                pane.setVvalue(v);
                if (attempt < 64) {
                    final double b = bottom;
                    Platform.runLater(() -> scrollBannerIntoView(attempt + 1, b));
                }
            });
        }

        private static List<Poker.Card> bestFiveCards(List<Poker.Card> cards) {
            if (cards.size() < 5) return List.of();
            return Poker.bestFive(cards).cards();
        }

        private static void renderRow(HBox row, List<Poker.Card> holeShown, List<Poker.Card> holeHidden,
                                      List<Poker.Card> upShown, int upHidden, List<Poker.Card> highlight) {
            row.getChildren().clear();
            for (Poker.Card card : holeShown) row.getChildren().add(tile(card, false, highlight.contains(card)));
            for (Poker.Card card : holeHidden) row.getChildren().add(tile(card, true, highlight.contains(card)));
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

        /** House rules: a popup in the standalone window, in-page view when embedded. */
        private void openRules() {
            if (stage == null && onRules != null) {
                onRules.run();
                return;
            }
            if (rulesStage != null && rulesStage.isShowing()) {
                rulesStage.toFront();
                return;
            }
            rulesStage = buildRulesStage();
            rulesStage.show();
        }

        /** The rules as a page node, for an embedded table to show in-page. */
        public Node rulesView() {
            if (rulesContent == null) {
                rulesContent = buildRulesContent();
            }
            return rulesContent;
        }

        private Stage buildRulesStage() {
            Stage rules = new Stage();
            if (stage != null) {
                rules.initOwner(stage);
                rules.initModality(Modality.WINDOW_MODAL);
            }
            rules.setTitle("Poker rules");
            rules.setResizable(false);

            VBox content = buildRulesContent();

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

        /** The house rules and hand rankings as page content, popup or in-page. */
        private VBox buildRulesContent() {
            VBox content = new VBox(8);
            content.setPadding(new Insets(16));
            content.getChildren().add(heading("How to play"));
            content.getChildren().addAll(rulesLine(
                    "You ante 50 points, and the dealer antes 50. Both go into the pot."),
                    rulesLine("Both sides get seven cards: two stay hidden, five are shown, one per round."),
                    rulesLine("The dealer only deals the cards and posts the ante; it never bets, so every "
                            + "decision is yours."),
                    rulesLine("Each of the six rounds you can Check (free, at most twice per hand), "
                            + "Raise (+50 to the pot and your stake), or Fold. After your action the "
                            + "next card is dealt straight away. There is no Call."),
                    rulesLine("Every Check and Raise flips one of your hidden hole cards face up."),
                    rulesLine("After the last round the best five of your seven cards is compared "
                            + "to the dealer's. The higher hand wins the pot."),
                    rulesLine("Losing at showdown costs your full stake; folding costs only half of it."),
                    rulesLine("Folding stops the betting, but the hand is still run out: the remaining "
                            + "cards are dealt and both hands are shown at the end."),
                    rulesLine("A raise that empties your stack is all-in: the remaining cards are dealt "
                            + "and the best five-card hands decide it."),
                    rulesLine("If both best five-card hands tie exactly, the pot pushes: nobody wins or loses."),
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
            return content;
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
