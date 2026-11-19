package rewards;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/** JavaFX Canvas renderer for the three scenes, cards, poker, upgrades, and ending. */
public final class PipeFlightCanvas extends Canvas {
    private static final Color GOLD = Color.web("#FFB71F");
    private static final Color SKY = Color.web("#86C9E8");
    private final GraphicsContext g = getGraphicsContext2D();
    private final SpriteAtlas sprites;

    public PipeFlightCanvas(SpriteAtlas sprites) {
        super(PipeFlightGame.WIDTH, PipeFlightGame.HEIGHT);
        this.sprites = sprites;
        g.setTextBaseline(VPos.TOP);
    }

    public void draw(PipeFlightGame game, int balance, int earnedToday) {
        background(game);
        if (game.drowning) {
            drowning(game, balance, earnedToday);
            return;
        }
        text("PIPE FLIGHT", 20, 18, 26, Color.WHITE, true);
        text("Flight score: " + game.score, 20, 53, 22, Color.WHITE, false);
        text("Cards: " + game.collected.size() + " / 8", 20, 83, 18, GOLD, false);
        String controls = switch (game.stage()) {
            case PipeFlightGame.AIR -> "W / SPACE flap   S dive   A / D steer";
            case PipeFlightGame.OCEAN -> "Hold SPACE: rise   Release: slow fall   A / D move";
            default -> "WASD / arrows: swim freely - no gravity";
        };
        text(controls, 285, 25, 17, Color.WHITE, false);
        text("Collect top or bottom tokens to build your hand", 285, 62, 15, Color.WHITE, false);
        text("Stage: " + game.stageName(), 285, 85, 16, GOLD, false);
        text("Poker: " + game.pokerPoints, 1090, 22, 22, GOLD, false);
        text(String.format("Speed: %.1fx", game.pipeSpeed / 180), 1090, 59, 18, Color.WHITE, false);
        text("Failures: " + game.failureCount + "/3", 1090, 86, 16, Color.LIGHTGRAY, false);
        g.setFill(Color.web("#2D2D2D"));
        g.fillRect(285, 103, 240, 6);
        g.setFill(game.stage() == PipeFlightGame.AIR ? Color.WHITE : Color.web("#4FE1BD"));
        g.fillRect(285, 103, 240 * game.health / 100.0, 6);
        text("HP " + game.health + "/100", 545, 85, 16, Color.WHITE, false);

        g.setStroke(Color.GRAY);
        g.strokeLine(0, PipeFlightGame.PLAY_TOP, PipeFlightGame.WIDTH, PipeFlightGame.PLAY_TOP);
        g.strokeLine(0, PipeFlightGame.PLAY_BOTTOM, PipeFlightGame.WIDTH, PipeFlightGame.PLAY_BOTTOM);
        g.save();
        g.beginPath();
        g.rect(0, PipeFlightGame.PLAY_TOP, PipeFlightGame.WIDTH,
                PipeFlightGame.PLAY_BOTTOM - PipeFlightGame.PLAY_TOP);
        g.closePath();
        g.clip();
        PipeFlightGame.Rect[] pipes = game.pipeRectangles();
        obstacleCard(pipes[0], game.topCard, true);
        obstacleCard(pipes[1], game.bottomCard, false);
        if (!game.topTaken) pickup(game.pickupX(), game.pickupY(true), game.topCard);
        if (!game.bottomTaken) pickup(game.pickupX(), game.pickupY(false), game.bottomCard);
        for (PipeFlightGame.Hazard hazard : game.hazards) hazard(hazard, game.sceneTime);
        sprite(game, game.playerX, game.playerY, 1, 0,
                game.hitGrace > 0 ? .6 : 1);
        g.restore();

        text("Power " + game.upgrades[0] + "   Armor " + game.upgrades[1]
                + "   Recovery " + game.upgrades[2] + "   Upgrades " + game.upgradesChosen
                + "/10   Card hit: next stage   M sound   ESC rewards", 20, 665, 18, Color.WHITE, false);
        text("Rewards: " + balance + "   Earned today: " + earnedToday + "/2000", 20, 692,
                16, GOLD, false);
        if (game.upgradeMenu) { upgradeMenu(game); return; }
        if (game.poker != null) { poker(game); return; }
        if (!game.gameOver && !game.lossReason.isEmpty())
            text(game.lossReason, 625, 692, 16, GOLD, false);
        if (game.gameOver) {
            g.setFill(Color.rgb(0, 0, 0, .72));
            g.fillRect(0, 0, 1280, 720);
            centered("FAILED", 245, 48, Color.RED, true);
            centered("Total pipes passed: " + game.totalPipes, 315, 26, Color.WHITE, false);
            if (game.canDouble()) {
                centered("DOUBLE OR NOTHING", 380, 26, Color.web("#FFB71F"), true);
                centered("Risk this run's " + game.pendingPips + " points on a coin flip", 420, 20, Color.WHITE, false);
                centered("[D] DOUBLE it      [N] KEEP it", 470, 24, Color.WHITE, true);
                centered("One gamble per day   R / ENTER: next run   ESC: rewards", 515, 18, Color.LIGHTGRAY, false);
                return;
            }
            if (game.doubled) {
                centered(game.doubleWon() ? "COIN FLIP WON: earnings doubled!"
                        : "COIN FLIP LOST: this run's points were forfeited.", 470, 20,
                        game.doubleWon() ? Color.web("#FFB71F") : Color.WHITE, true);
            }
            centered("R / ENTER: play again for 500 points", game.doubled ? 515 : 375, 24, Color.WHITE, false);
            centered("ESC: return to rewards", game.doubled ? 555 : 415, 22, Color.WHITE, false);
        }
    }

    private void background(PipeFlightGame game) {
        g.setFill(Color.BLACK);
        g.fillRect(0, 0, 1280, 720);
        g.save();
        g.beginPath();
        g.rect(0, 128, 1280, 512);
        g.closePath();
        g.clip();
        double time = game.sceneTime;
        if (game.stage() == PipeFlightGame.AIR) {
            block(1064, 144, 44, 44, gray(125));
            block(1077, 137, 23, 58, gray(125));
            for (int layer = 0; layer < 2; layer++) {
                for (int i = 0; i < 16; i++) {
                    double x = i * 100 - (time * (7 + layer * 7)) % 100 - 100;
                    double height = 112 + (i * 53 + layer * 79) % 220;
                    double y = 640 - height + layer * 35;
                    Color shade = gray(18 + layer * 17);
                    block(x, y, 76 + layer * 14, 640 - y, shade);
                    block(x + 18, y - 12, 30, 12, shade);
                    if (i % 3 == 0) block(x + 35, y - 44, 3, 32, Color.GRAY);
                    for (int row = 0; row < 6; row++)
                        for (int col = 0; col < 4; col++) {
                            if ((i + row * 3 + col) % 4 == 0) continue;
                            double wy = y + 18 + row * 34;
                            if (wy < 625) block(x + 10 + col * 18, wy, 6, 11, gray(84));
                        }
                }
            }
            block(0, 632, 1280, 8, gray(67));
        } else if (game.stage() == PipeFlightGame.OCEAN) {
            block(0, 128, 1280, 170, Color.rgb(68, 118, 145));
            block(0, 298, 1280, 342, Color.rgb(17, 77, 109));
            block(0, 298, 1280, 6, Color.rgb(172, 217, 221));
            for (int i = 0; i < 8; i++) {
                double x = i * 225 - (time * 10) % 225;
                double y = 164 + (i % 2) * 26;
                block(x, y, 86, 12, Color.rgb(143, 184, 199));
                block(x + 22, y - 10, 42, 10, Color.rgb(143, 184, 199));
            }
            for (int row = 0; 314 + row * 33 < 632; row++) {
                for (int i = 0; i < 11; i++) {
                    double x = i * 150 + (row % 2) * 55 - (time * (15 + row * 2)) % 150;
                    double y = 314 + row * 33;
                    block(x, y, 34 + row * 3, 3, Color.rgb(45, 125, 150));
                    block(x + 14, y + 4, 28, 2, Color.rgb(98, 171, 180));
                }
            }
        } else {
            for (int row = 0; row < 8; row++)
                block(0, 128 + row * 64, 1280, 64, Color.rgb(8 + row, 76 - row * 6, 113 - row * 7));
            for (int i = 0; i < 12; i++)
                block(i * 124 + Math.sin(time * .4 + i) * 14, 128, 12, 140, Color.rgb(23, 85, 117));
            block(0, 625, 1280, 15, Color.rgb(32, 51, 55));
            for (int i = 0; i < 18; i++) {
                double x = i * 76 + 12, height = 20 + (i * 17) % 52;
                block(x, 640 - height, 5, height, Color.rgb(41, 103, 81));
                block(x - 7, 650 - height, 9, 5, Color.rgb(49, 124, 96));
                block(x + 3, 664 - height, 10, 5, Color.rgb(49, 124, 96));
            }
            for (int i = 0; i < 18; i++) {
                double x = 35 + i * 77;
                double y = 158 + (((i * 71 + 430 - time * 15) % 400) + 400) % 400;
                Color bubble = Color.rgb(58, 123, 147);
                block(x, y, 5, 2, bubble); block(x - 2, y + 2, 2, 5, bubble);
                block(x + 5, y + 2, 2, 5, bubble); block(x, y + 7, 5, 2, bubble);
            }
        }
        g.restore();
    }

    private void obstacleCard(PipeFlightGame.Rect bounds, Poker.Card card, boolean top) {
        g.setFill(Color.WHITE);
        g.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        g.setStroke(Color.BLACK);
        g.setLineWidth(3);
        g.strokeRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        if (card == null) return;
        int fontSize = bounds.height() < 70 ? 18 : 26;
        double y = top ? Math.max(bounds.y() + 5, bounds.y() + bounds.height() - fontSize * 2 - 10)
                : bounds.y() + 7;
        Color color = card.red() ? Color.RED : Color.BLACK;
        text(card.value(), bounds.x() + 10, y, fontSize, color, true);
        text(card.suitLetter(), bounds.x() + bounds.width() - 28, y + fontSize, fontSize, color, true);
    }

    private void pickup(double x, double y, Poker.Card card) {
        if (card == null) return;
        g.setFill(GOLD); g.fillOval(x - 16, y - 16, 32, 32);
        g.setFill(Color.WHITE); g.fillOval(x - 13, y - 13, 26, 26);
        centeredAt(card.value() + card.suitLetter(), x, y - 7, 13,
                card.red() ? Color.RED : Color.BLACK, true);
    }

    private void hazard(PipeFlightGame.Hazard h, double time) {
        double x = h.x, y = h.y;
        if (h.kind == PipeFlightGame.AIR) {
            block(x + 3, y - 12, 3, 7, Color.WHITE);
            block(x, y - 5, 7, 12, Color.WHITE);
            block(x - 2, y + 7, 7, 4, Color.WHITE);
        } else if (h.kind == PipeFlightGame.OCEAN) {
            double wing = Math.sin(time * 9 + h.age * 3) * 6;
            block(x - 7, y - 2, 14, 6, Color.WHITE);
            block(x - 20, y - 6 - wing, 13, 4, Color.WHITE);
            block(x + 7, y - 6 - wing, 13, 4, Color.WHITE);
            block(x - 15, y - 3 - wing, 8, 4, Color.WHITE);
            block(x + 7, y - 3 - wing, 8, 4, Color.WHITE);
            block(x - 10, y - 4, 5, 6, Color.WHITE);
            block(x - 15, y - 1, 6, 3, Color.rgb(239, 181, 87));
            block(x - 9, y - 3, 2, 2, Color.BLACK);
        } else {
            block(x - 12, y - 6, 24, 12, Color.rgb(217, 162, 68));
            block(x - 7, y - 9, 14, 18, Color.rgb(217, 162, 68));
            block(x + 12, y - 3, 5, 6, Color.rgb(193, 119, 46));
            block(x + 17, y - 8, 5, 16, Color.rgb(193, 119, 46));
            block(x - 7, y - 3, 3, 3, Color.BLACK);
            block(x - 2, y - 4, 9, 2, Color.rgb(254, 212, 111));
        }
    }

    private void sprite(PipeFlightGame game, double x, double y, double scale, double angle, double alpha) {
        sprite(game, x, y, scale, angle, alpha,
                sprites.image(game.stage(), game.animationFrame));
    }

    private void sprite(PipeFlightGame game, double x, double y, double scale, double angle,
                        double alpha, Image image) {
        int size = game.playerSize();
        g.save();
        g.setGlobalAlpha(alpha);
        g.translate(x + size / 2.0, y + size / 2.0);
        g.rotate(angle);
        g.scale(game.facingLeft ? -1 : 1, 1);
        g.drawImage(image,
                -size * scale / 2, -size * scale / 2, size * scale, size * scale);
        g.restore();
    }

    private void drowning(PipeFlightGame game, int balance, int earnedToday) {
        text("Poker: " + game.pokerPoints, 1090, 22, 22, GOLD, false);
        double progress = Math.min(1, game.drowningTime / 3.5);
        int tint = (int) (game.drowningTime / .6) % 3;
        sprite(game, game.playerX, game.playerY, 1 - .5 * progress,
                game.drowningTime * 360, 1 - progress * .5,
                sprites.drowningImage(game.animationFrame, tint));
        if (!game.gameOver) {
            g.setStroke(SKY.deriveColor(0, 1, 1, 1 - progress));
            for (int i = 0; i < 5; i++) {
                double y = game.playerY + 28 - i * 22 - game.drowningTime * 28;
                g.strokeOval(625 + i * 8, y, 6 + i % 2 * 2, 6 + i % 2 * 2);
            }
        }
        centered(game.gameOver ? "FAILED" : "DROWNING...", 165, 42, Color.RED, true);
        if (game.gameOver) {
            centered("Total pipes: " + game.totalPipes + "   Poker: " + game.pokerPoints,
                    545, 24, Color.WHITE, false);
            if (game.canDouble()) {
                centered("DOUBLE OR NOTHING: risk " + game.pendingPips + " points", 600, 22,
                        Color.web("#FFB71F"), true);
                centered("[D] DOUBLE   [N] KEEP   R / ENTER: next run   ESC: rewards", 640, 20,
                        Color.WHITE, false);
                return;
            }
            centered("R / ENTER: play again for 500 points    ESC: rewards", 600, 22, Color.WHITE, false);
        }
        text("Rewards: " + balance + "   Earned today: " + earnedToday + "/2000", 20, 690,
                16, GOLD, false);
    }

    private void poker(PipeFlightGame game) {
        Poker.Round p = game.poker;
        g.setFill(Color.rgb(30, 30, 46)); g.fillRect(12, 12, 1256, 696);
        g.setStroke(GOLD); g.setLineWidth(2); g.strokeRect(12, 12, 1256, 696);
        text(p.phase == Poker.Phase.DISCARD ? "POKER - CHOOSE YOUR DISCARDS"
                : "POKER - FLIGHT PAUSED", 30, 27, 28, Color.WHITE, true);
        text("Poker points: " + game.pokerPoints, 1030, 32, 22, GOLD, false);
        centered("Failures: " + game.failureCount + "/3   Poker losses: " + game.pokerLosses
                + "   Used: " + game.usedCards.size() + "   Available: "
                + (52 - game.usedCards.size()), 92, 18, Color.LIGHTGRAY, false);
        int handX = (1280 - (7 * 88 + 72)) / 2;
        if (p.phase == Poker.Phase.DISCARD) {
            text("Your eight cards - gold border means kept", handX, 126, 20, Color.WHITE, false);
            for (int i = 0; i < 8; i++) {
                boolean held = p.hold.get(i);
                pokerCard(p.playerCards.get(i), handX + i * 88, 164, false, held);
                text(held ? "KEEP" : "DRAW", handX + i * 88 + 22, 258, 16,
                        held ? GOLD : Color.GRAY, false);
            }
            int kept = 0;
            for (boolean h : p.hold) if (h) kept++;
            Poker.BestHand value = p.currentHoldValue();
            text(value == null ? "Keeping " + kept + " of 8 - need five to show a hand"
                    : "Keeping " + kept + " - your best five: " + value.rank().name(),
                    handX, 285, 22, GOLD, false);
        } else {
            text("Your eight cards - gold border marks your best five", handX, 126, 20,
                    Color.WHITE, false);
            for (int i = 0; i < 8; i++) pokerCard(p.playerCards.get(i), handX + i * 88, 164,
                    false, p.playerBest.cards().contains(p.playerCards.get(i)));
            text("Your hand: " + p.playerBest.rank().name(), handX, 269, 22, GOLD, false);
        }
        text("Machine's eight cards", handX, 327, 20, Color.WHITE, false);
        for (int i = 0; i < 8; i++) pokerCard(p.dealerCards.get(i), handX + i * 88, 362,
                !p.revealed, p.revealed && p.dealerBest.cards().contains(p.dealerCards.get(i)));
        if (p.revealed) {
            text("Machine hand: " + p.dealerBest.rank().name(), handX, 468, 22, Color.WHITE, false);
            if (p.powerUsed) text("POWER: machine hand was rerolled once", handX, 502, 16, SKY, false);
            centered(p.outcome, 540, 22, GOLD, false);
            centered(p.upgradeDue ? "ENTER / SPACE: choose your upgrade"
                    : "ENTER / SPACE: return to flight", 600, 22, Color.WHITE, false);
        } else if (p.phase == Poker.Phase.DISCARD) {
            centered("1-8: keep or discard each card   ENTER / SPACE: commit the draw",
                    540, 22, GOLD, false);
            centered("Discarded cards are replaced by fresh draws from the deck.",
                    600, 18, Color.WHITE, false);
        } else {
            centered("ENTER / SPACE: reveal both best five-card hands", 540, 22, GOLD, false);
            centered("Win: +1 poker coin, keep flying. Loss: advance stage.", 600, 18,
                    Color.WHITE, false);
        }
        centered("C = Clubs   D = Diamonds   H = Hearts   S = Spades", 670,
                16, Color.LIGHTGRAY, false);
    }

    private void pokerCard(Poker.Card card, double x, double y, boolean hidden, boolean best) {
        g.setFill(hidden ? Color.web("#451C69") : Color.WHITE);
        g.fillRect(x, y, 72, 92);
        g.setStroke(hidden ? Color.WHITE : best ? GOLD : Color.BLACK);
        g.setLineWidth(best ? 3 : 2);
        g.strokeRect(x, y, 72, 92);
        if (hidden) { text("?", x + 23, y + 27, 36, Color.WHITE, true); return; }
        Color color = card.red() ? Color.RED : Color.BLACK;
        text(card.value(), x + 8, y + 8, 24, color, true);
        text(card.suitLetter(), x + 42, y + 55, 24, color, true);
    }

    private void upgradeMenu(PipeFlightGame game) {
        g.setFill(Color.rgb(30, 30, 46)); g.fillRect(12, 12, 1256, 696);
        g.setStroke(GOLD); g.setLineWidth(2); g.strokeRect(12, 12, 1256, 696);
        text("POKER COIN - CHOOSE ONE UPGRADE", 140, 40, 28, GOLD, true);
        text("Coin " + game.pokerPoints + " / 10 - flight is paused", 140, 84, 22, Color.WHITE, false);
        String[] names = {"Power", "Armor", "Recovery"};
        String[] evolved = {"Vengeance", "Aegis", "Regeneration"};
        String[] descriptions = {
                "On a poker loss, chance to reroll the machine's eight cards once.",
                "On a rain, gull or fish hit, chance to block damage and gain 1.5s protection.",
                "After hazard damage, chance to heal 20 health before the stage ends."
        };
        for (int i = 0; i < 3; i++) {
            int level = game.upgrades[i], y = 152 + i * 120;
            String name = level >= 5 ? evolved[i] : names[i];
            text("[" + (i + 1) + "] " + name + "   Level " + level + " -> " + (level + 1),
                    140, y, 25, Color.WHITE, true);
            text(String.format("Chance: %.0f%% -> %.0f%% (maximum 40%%)",
                    PipeFlightGame.upgradeChance(level) * 100,
                    PipeFlightGame.upgradeChance(level + 1) * 100),
                    140, y + 36, 20, GOLD, false);
            text(descriptions[i], 140, y + 67, 18, Color.LIGHTGRAY, false);
        }
        text("Press 1, 2 or 3. First ten coins only; later wins still earn points.",
                140, 630, 20, Color.WHITE, false);
        text("Upgrades and coins stay with you when starting another paid play in this app session.",
                140, 668, 18, Color.LIGHTGRAY, false);
    }

    private void block(double x, double y, double width, double height, Color color) {
        g.setFill(color); g.fillRect(x, y, width, height);
    }
    private static Color gray(int value) { return Color.rgb(value, value, value); }
    private void text(String value, double x, double y, int size, Color color, boolean bold) {
        g.setFont(Font.font("SansSerif", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        g.setFill(color);
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(value, x, y);
    }
    private void centered(String value, double y, int size, Color color, boolean bold) {
        centeredAt(value, 640, y, size, color, bold);
    }
    private void centeredAt(String value, double x, double y, int size, Color color, boolean bold) {
        g.setFont(Font.font("SansSerif", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        g.setFill(color);
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(value, x, y);
        g.setTextAlign(TextAlignment.LEFT);
    }
}
