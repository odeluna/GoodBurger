# Rewards Screen + Pipe Flight (JavaFX)

This project replaces the earlier Swing app with JavaFX controls and a JavaFX Canvas game. The mini-game follows the **extended** Go version in `~/Projects/pipe-flight-running-sound/pipe-flight-noir-ocean (2)`. It has noir city, ocean, and underwater stages; pixel-aware collisions; hazards and health; eight-card poker; Power, Armor, and Recovery upgrades; audio; and the final drowning scene. The original PNG sprites and WAV files are copied into `src/main/resources`.

## Run

On Linux x64 with JDK 26:

```bash
./run.sh
```

JavaFX 26 Linux libraries are included in `lib/`, so running the project does not require downloading dependencies. To rebuild from source, run `./build.sh`. To run the logic checks, run `./test.sh`.

## Rewards rules

- The starting balance is **500 points**.
- Each Pipe Flight play **spends 500 points**, including retries with R or Enter.
- Each pipe passed awards **1 rewards point** immediately. Poker coins remain a separate in-game score.
- Pipe Flight can award at most **2,000 rewards points per local calendar day**. The game button becomes unavailable after that cap is reached, and the cap resets on the next local day.
- Balance, daily earnings, and redemptions persist in `~/.local/share/rewards-javafx/progress.properties`. Game campaign progress and upgrades last only for the current app session, matching the Go game's restart behavior.

## Game controls

| Stage | Controls |
|---|---|
| Noir city | Tap W, Up, or Space to flap; hold S or Down to dive; A/D or Left/Right to steer. |
| Ocean | Hold W, Up, or Space to rise; release to fall slowly; S dives; A/D steers. |
| Underwater | W/S or Up/Down to swim vertically; A/D or Left/Right to steer. |

In poker, Enter or Space reveals and then continues. After a poker win, 1, 2, or 3 selects an upgrade. M toggles sound. Esc returns to rewards. After FAILED, R or Enter starts another paid play if the balance and daily cap allow it.

## UML

Class diagrams live in [`docs/UML/`](docs/UML/):

- `goodburger-full.png` — integrated model merging all source diagrams into one page: Application & Navigation, Menu Customization & Checkout, Accounts & Shared User, and Rewards/Tasks/Points (with a relationship legend).
- `rewards-class-diagram.png` — RewardsJavaFX class diagram (RewardsApp, MainView, RewardsController, data models `UserModel`/`Task`/`RewardItem`, and `PointsService`/`TaskService`).
- `menu-checkout.png` — GoodBurger menu/checkout layer diagram: `GoodBurgerApplication` (extends `Application`), `CheckoutScreenController` bound to `checkout_page.fxml`, `MenuItemManager` composing `MenuItem`, `MenuItem` referencing `Item` ingredients, and `Order` composing ordered `MenuItem`s for a `User`.
- `rewards-nogame.puml` — PlantUML source (source of truth: `src/main/java/rewards/*.java`)
- `rewards-nogame.svg` / `rewards-nogame.png` — rendered diagram

![GoodBurger integrated class diagram](docs/UML/goodburger-full.png)

Relationships follow the CS 3443 arrow vocabulary: composition (`*--`, has-a/owned), dependency (`..>`), generalization (`--|>`, `extends`), and realization (`..|>`, `implements`).

## Design

The [Figma design](https://www.figma.com/design/4qoghK6Hi24ovxvCw7Nmnl/Rewards---Pipe-Flight---JavaFX-UI) contains the original editable vector rewards layout alongside screenshots of the JavaFX rewards screen, all three game stages, poker, upgrades, and the ending. These JavaFX screenshots are also in `previews/`.

The OpenJFX runtime in `lib/` comes from the [official Maven Central OpenJFX 26 artifacts](https://repo.maven.apache.org/maven2/org/openjfx/). These files are platform-specific; on another operating system, replace them with that platform's JavaFX 26 modules.
