package rewards;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Three-stage Pipe Flight rules translated from the extended Go project. No UI dependencies. */
public final class PipeFlightGame {
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;
    public static final double PLAY_TOP = 128;
    public static final double PLAY_BOTTOM = 640;
    public static final double PIPE_WIDTH = 96;
    public static final double GAP_HEIGHT = 256;
    public static final int AIR = 0, OCEAN = 1, WATER = 2, DROWNING = 3;
    private static final double START_MOVE_SPEED = 360, START_PIPE_SPEED = 180;
    private static final double MAX_MOVE_SPEED = 840, MAX_PIPE_SPEED = 480;
    private static final String[] UPGRADE_NAMES = {"Power", "Armor", "Recovery"};

    final SpriteAtlas sprites;
    final Random random;
    double sceneTime, hazardTimer = .6, drowningTime;
    double playerX = 180, playerY, velocityY;
    double pipeX, gapY;
    double moveSpeed = START_MOVE_SPEED, pipeSpeed = START_PIPE_SPEED;
    double verticalInput, hitGrace, animationTime;
    int health = 100, score, totalPipes, pokerPoints, pokerWins, pokerLosses, failureCount;
    int upgradesChosen, resetSerial, animationFrame;
    final int[] upgrades = new int[3];
    boolean scored, gameOver, drowning, facingLeft, holdJump, upgradeMenu;
    boolean doubled, doubleOffered, doubleWon;
    int pendingPips, pendingBonus;
    String lossReason = "";
    Poker.Deck deck;
    Poker.Card topCard, bottomCard;
    boolean topTaken, bottomTaken;
    final List<Poker.Card> collected = new ArrayList<>();
    final List<Poker.Card> usedCards = new ArrayList<>();
    final List<Hazard> hazards = new ArrayList<>();
    Poker.Round poker;

    public record Input(boolean flap, boolean up, boolean down, boolean left, boolean right) { }
    public record Rect(double x, double y, double width, double height) { }

    public static final class Hazard {
        double x, y, vx, vy, age, baseY;
        final int kind;
        Hazard(int kind) { this.kind = kind; }
        double radius() { return kind == AIR ? 10 : 18; }
        String label() { return new String[]{"Rain", "Seagull", "Fish"}[Math.min(kind, 2)]; }
    }

    public PipeFlightGame(Random random, SpriteAtlas sprites) {
        this.random = random;
        this.sprites = sprites;
        playerY = (PLAY_TOP + PLAY_BOTTOM - 128) / 2;
        resetPipe();
    }

    public int stage() { return Math.min(failureCount, DROWNING); }
    public int playerSize() { return sprites.size(stage()); }
    public int score() { return score; }
    public int totalPipes() { return totalPipes; }
    public boolean gameOver() { return gameOver; }
    public boolean canDouble() { return doubleOffered && !doubled; }
    public boolean doubleWon() { return doubleWon; }
    /** The UI arms the gamble when a run ends; one coin flip per run. */
    public void offerGamble() {
        if (!doubled) doubleOffered = true;
    }
    /** Resolves the coin flip; called by the UI with the flip result. */
    public void resolveGamble(boolean win) {
        if (!canDouble()) return;
        doubleWon = win;
        doubled = true;
        doubleOffered = false;
    }
    /** UI hook: award account-wide poker-win achievements; set before playing. */
    java.util.function.Supplier<java.util.List<ProgressStore.AchievementEvent>> onPokerWin;
    public String stageName() {
        return new String[]{"128px NOIR", "64px OCEAN", "32px UNDERWATER", "DROWNING"}[stage()];
    }

    public void update(double elapsed, Input input) {
        if (gameOver || drowning || poker != null || upgradeMenu) return;
        double dt = Math.max(0, Math.min(elapsed, 1.0 / 30));
        double horizontal = (input.right ? 1 : 0) - (input.left ? 1 : 0);
        if (horizontal != 0) facingLeft = horizontal < 0;
        verticalInput = (input.down ? 1 : 0) - (input.up ? 1 : 0);
        holdJump = input.up;
        if (stage() == AIR) {
            if (input.flap && !input.down) velocityY = -380;
            if (input.down) velocityY = Math.max(velocityY, 160);
        }
        int oldStage = stage(), oldSerial = resetSerial;
        while (dt > 0 && oldSerial == resetSerial && oldStage == stage()
                && !gameOver && !drowning && poker == null && !upgradeMenu) {
            double step = Math.min(dt, 1.0 / 120);
            step(step, horizontal, input.down);
            dt -= step;
        }
        if (!gameOver && !drowning && poker == null && !upgradeMenu) {
            animationTime += Math.max(0, Math.min(elapsed, 1.0 / 30));
            while (animationTime >= 1.0 / 8) {
                animationTime -= 1.0 / 8;
                animationFrame = (animationFrame + 1) % 4;
            }
        }
    }

    void step(double dt, double horizontal, boolean dive) {
        if (gameOver || drowning || poker != null || upgradeMenu) return;
        hitGrace = Math.max(0, hitGrace - dt);
        sceneTime += dt;
        movePlayer(dt, horizontal, dive);
        pipeX -= pipeSpeed * dt;
        moveHazards(dt);
        for (Rect pipe : pipeRectangles()) {
            if (touchesObstacle(pipe)) {
                failFlight("You hit a card.");
                return;
            }
        }
        for (Iterator<Hazard> iterator = hazards.iterator(); iterator.hasNext();) {
            Hazard hazard = iterator.next();
            if (hitGrace <= 0 && touchesCircle(hazard.x, hazard.y, hazard.radius())) {
                iterator.remove();
                takeHazardDamage(hazard.label());
                return;
            }
        }
        if (!scored && pipeX + PIPE_WIDTH < playerX) {
            score++;
            totalPipes++;
            scored = true;
            moveSpeed = Math.min(MAX_MOVE_SPEED, moveSpeed + 24);
            pipeSpeed = Math.min(MAX_PIPE_SPEED, pipeSpeed + 24);
        }
        collectCards();
        if (poker != null) return;
        if (pipeX + PIPE_WIDTH < 0) resetPipe();
    }

    private void movePlayer(double dt, double horizontal, boolean dive) {
        switch (stage()) {
            case AIR -> {
                velocityY = Math.min(680, velocityY + (1050 + (dive ? 1500 : 0)) * dt);
                playerY += velocityY * dt;
            }
            case OCEAN -> {
                if (dive) velocityY = Math.min(250, Math.max(velocityY, 120) + 1690 * dt);
                else if (holdJump) velocityY = -220;
                else velocityY = Math.min(110, Math.max(0, velocityY) + 190 * dt);
                playerY += velocityY * dt;
            }
            default -> {
                velocityY = 0;
                playerY += verticalInput * moveSpeed * dt;
            }
        }
        playerX = clamp(playerX + horizontal * moveSpeed * dt, 0, WIDTH - playerSize());
        if (playerY < PLAY_TOP) { playerY = PLAY_TOP; velocityY = 0; }
        if (playerY > PLAY_BOTTOM - playerSize()) {
            playerY = PLAY_BOTTOM - playerSize();
            velocityY = 0;
        }
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(value, high));
    }

    public Rect[] pipeRectangles() {
        return new Rect[]{new Rect(pipeX, PLAY_TOP, PIPE_WIDTH, gapY - PLAY_TOP),
                new Rect(pipeX, gapY + GAP_HEIGHT, PIPE_WIDTH, PLAY_BOTTOM - gapY - GAP_HEIGHT)};
    }

    private void resetPipe() {
        if (deck != null) {
            if (!topTaken && topCard != null) deck.addBottom(topCard);
            if (!bottomTaken && bottomCard != null) deck.addBottom(bottomCard);
        }
        topCard = bottomCard = null;
        pipeX = WIDTH + 16;
        int minimum = (int) PLAY_TOP + 36;
        int maximum = (int) (PLAY_BOTTOM - GAP_HEIGHT) - 36;
        gapY = minimum + random.nextInt(maximum - minimum + 1);
        scored = false;
        ensureCards(2);
        topCard = deck.drawTop();
        bottomCard = deck.drawBottom();
        topTaken = bottomTaken = false;
    }

    private void ensureCards(int count) {
        if (deck != null && deck.size() >= count) return;
        List<Poker.Card> excluded = new ArrayList<>(usedCards);
        if (topCard != null) excluded.add(topCard);
        if (bottomCard != null) excluded.add(bottomCard);
        excluded.addAll(collected);
        deck = new Poker.Deck(random, excluded);
    }

    public double pickupX() { return pipeX + PIPE_WIDTH / 2; }
    public double pickupY(boolean top) { return gapY + (top ? 64 : GAP_HEIGHT - 64); }

    private void collectCards() {
        if (poker != null || stage() == DROWNING) return;
        if (!topTaken && touchesCircle(pickupX(), pickupY(true), 13)) {
            topTaken = true;
            collected.add(topCard);
            if (collected.size() == 8) { beginPoker(); return; }
        }
        if (!bottomTaken && touchesCircle(pickupX(), pickupY(false), 13)) {
            bottomTaken = true;
            collected.add(bottomCard);
            if (collected.size() == 8) beginPoker();
        }
    }

    private void spawnHazard() {
        Hazard h = new Hazard(stage());
        if (h.kind == AIR) {
            h.x = 32 + random.nextDouble() * (WIDTH - 64);
            h.y = PLAY_TOP + 12;
            h.vx = -65;
            h.vy = 235;
        } else {
            h.x = WIDTH + 24;
            h.y = PLAY_TOP + 40 + random.nextDouble() * (PLAY_BOTTOM - PLAY_TOP - 80);
            h.vx = -pipeSpeed - 65;
        }
        h.baseY = h.y;
        hazards.add(h);
    }

    private void moveHazards(double dt) {
        hazardTimer -= dt;
        if (hazardTimer <= 0) {
            spawnHazard();
            hazardTimer = new double[]{.35, 1.2, 1.0}[Math.min(stage(), 2)];
        }
        for (Iterator<Hazard> iterator = hazards.iterator(); iterator.hasNext();) {
            Hazard h = iterator.next();
            h.age += dt;
            h.x += h.vx * dt;
            h.y = h.kind == AIR ? h.y + h.vy * dt : h.baseY + Math.sin(h.age * 3) * 14;
            if (h.x < -40 || h.y > PLAY_BOTTOM + 40) iterator.remove();
        }
    }

    private void takeHazardDamage(String name) {
        if (hitGrace > 0 || poker != null || upgradeMenu || drowning || gameOver) return;
        if (triggerUpgrade(1)) {
            hitGrace = 1.5;
            lossReason = "Armor blocked " + name + ".";
            return;
        }
        health = Math.max(0, health - 20);
        hitGrace = .7;
        lossReason = name + ": -20 health.";
        if (triggerUpgrade(2)) {
            health = Math.min(100, health + 20);
            lossReason = "Recovery healed 20 health.";
        }
        if (health <= 0) {
            failureCount++;
            lossReason = "Health depleted. Next stage; flight reset.";
            resetFlight();
        }
    }

    private void failFlight(String reason) {
        if (drowning || gameOver || poker != null || upgradeMenu) return;
        health = 0;
        failureCount++;
        lossReason = reason + " Stage ended; flight reset.";
        resetFlight();
    }

    private void resetFlight() {
        resetSerial++;
        hitGrace = 0;
        hazardTimer = .6;
        sceneTime = 0;
        moveSpeed = START_MOVE_SPEED;
        pipeSpeed = START_PIPE_SPEED;
        poker = null;
        collected.clear();
        deck = null;
        topCard = bottomCard = null;
        hazards.clear();
        velocityY = verticalInput = 0;
        holdJump = facingLeft = false;
        if (stage() == DROWNING) {
            startDrowning();
            return;
        }
        health = 100;
        score = 0;
        playerX = 180;
        playerY = (PLAY_TOP + PLAY_BOTTOM - playerSize()) / 2;
        resetPipe();
    }

    private void startDrowning() {
        drowning = true;
        drowningTime = 0;
        playerX = (WIDTH - playerSize()) / 2.0;
        playerY = (PLAY_TOP + PLAY_BOTTOM - playerSize()) / 2;
        velocityY = 0;
    }

    public void advanceDrowning(double dt) {
        if (!drowning || gameOver) return;
        double step = Math.max(0, Math.min(dt, 3.5 - drowningTime));
        drowningTime += step;
        sceneTime += step;
        if (drowningTime >= 3.5) {
            gameOver = true;
            lossReason = "Drowned. FAILED.";
        }
    }

    public boolean touchesObstacle(Rect obstacle) {
        int size = playerSize();
        if (playerX >= obstacle.x + obstacle.width || playerX + size <= obstacle.x
                || playerY >= obstacle.y + obstacle.height || playerY + size <= obstacle.y) return false;
        int x0 = Math.max(0, (int) Math.floor(obstacle.x - playerX));
        int y0 = Math.max(0, (int) Math.floor(obstacle.y - playerY));
        int x1 = Math.min(size, (int) Math.ceil(obstacle.x + obstacle.width - playerX));
        int y1 = Math.min(size, (int) Math.ceil(obstacle.y + obstacle.height - playerY));
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                if (sprites.opaque(stage(), animationFrame, facingLeft, x, y)) return true;
        return false;
    }

    public boolean touchesCircle(double cx, double cy, double radius) {
        int size = playerSize();
        double nearX = clamp(cx, playerX, playerX + size);
        double nearY = clamp(cy, playerY, playerY + size);
        if (Math.pow(cx - nearX, 2) + Math.pow(cy - nearY, 2) > radius * radius) return false;
        int x0 = Math.max(0, (int) Math.floor(cx - radius - playerX) - 1);
        int y0 = Math.max(0, (int) Math.floor(cy - radius - playerY) - 1);
        int x1 = Math.min(size, (int) Math.ceil(cx + radius - playerX) + 1);
        int y1 = Math.min(size, (int) Math.ceil(cy + radius - playerY) + 1);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (!sprites.opaque(stage(), animationFrame, facingLeft, x, y)) continue;
                double px = playerX + x, py = playerY + y;
                double dx = cx - clamp(cx, px, px + 1);
                double dy = cy - clamp(cy, py, py + 1);
                if (dx * dx + dy * dy <= radius * radius) return true;
            }
        }
        return false;
    }

    void beginPoker() {
        if (poker != null || collected.size() != 8) return;
        ensureCards(8);
        poker = new Poker.Round(collected, deck);
    }

    public void pokerAction() {
        if (poker == null) return;
        if (poker.revealed) resumeFlight();
        else revealPoker();
    }

    private void revealPoker() {
        Poker.Round round = poker;
        if (round == null || round.revealed) return;
        round.revealed = true;
        if (round.dealerBest.rank().compareTo(round.playerBest.rank()) > 0 && triggerUpgrade(0)) {
            rerollDealer(round);
        }
        int result = round.playerBest.rank().compareTo(round.dealerBest.rank());
        if (result > 0) {
            pokerPoints++;
            pokerWins++;
            if (onPokerWin != null) onPokerWin.get();
            round.upgradeDue = pokerPoints <= 10;
            round.outcome = round.upgradeDue
                    ? "YOU WIN! +1 poker coin. Choose an upgrade next."
                    : "YOU WIN! +1 poker coin. Upgrade limit reached (10).";
        } else if (result < 0) {
            pokerLosses++;
            failureCount++;
            health = 0;
            round.lost = true;
            usedCards.addAll(round.playerCards);
            usedCards.addAll(round.dealerCards);
            round.outcome = switch (stage()) {
                case OCEAN -> "OCEAN: 64px, 100 health. Hold SPACE; release to fall.";
                case WATER -> "UNDERWATER: 32px orange, 100 health. WASD to swim.";
                default -> "FINAL FAILURE: the character is drowning.";
            };
        } else round.outcome = "TIE. No poker point; your current speed carries on.";
    }

    private void rerollDealer(Poker.Round round) {
        for (Poker.Card card : round.dealerCards) deck.addBottom(card);
        deck.shuffle(random);
        round.dealerCards.clear();
        for (int i = 0; i < 8; i++) round.dealerCards.add(deck.drawTop());
        round.dealerBest = Poker.bestFive(round.dealerCards);
        round.powerUsed = true;
    }

    private void resumeFlight() {
        if (poker == null || !poker.revealed) return;
        Poker.Round round = poker;
        poker = null;
        if (round.lost) { resetFlight(); return; }
        for (Poker.Card card : round.playerCards) {
            if (!deck.contains(card) && !usedCards.contains(card)) deck.addBottom(card);
        }
        for (Poker.Card card : round.dealerCards) {
            if (!deck.contains(card) && !usedCards.contains(card)) deck.addBottom(card);
        }
        collected.clear();
        upgradeMenu = round.upgradeDue;
    }

    public static double upgradeChance(int level) {
        if (level <= 0) return 0;
        return Math.min(level == 1 ? .1 : level == 2 ? .2 : .4, .4);
    }

    private boolean triggerUpgrade(int which) {
        double chance = upgradeChance(upgrades[which]);
        return chance > 0 && random.nextDouble() < chance;
    }

    public boolean chooseUpgrade(int which) {
        if (!upgradeMenu || which < 0 || which >= 3 || upgradesChosen >= 10) return false;
        upgrades[which]++;
        upgradesChosen++;
        upgradeMenu = false;
        lossReason = UPGRADE_NAMES[which] + " upgraded to level " + upgrades[which] + ".";
        return true;
    }

    /** A paid new play keeps poker coins/upgrades, as R did in the Go app session. */
    public PipeFlightGame restartCampaign() {
        PipeFlightGame next = new PipeFlightGame(random, sprites);
        next.pokerPoints = pokerPoints;
        next.pokerWins = pokerWins;
        System.arraycopy(upgrades, 0, next.upgrades, 0, 3);
        next.upgradesChosen = upgradesChosen;
        return next;
    }
}
