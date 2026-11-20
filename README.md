# GoodBurger (JavaFX)

GoodBurger is a restaurant ordering app (sign-in, menu, checkout, profile) with a Rewards page that contains two mini games:

- **Pipe Flight** — a JavaFX Canvas game with noir-city, ocean, and underwater stages (hazards and health, Power/Armor/Recovery upgrades, audio, the final drowning scene).
- **Poker** — a seven-card stud game played against the dealer.

This branch is the **FirstEdition_V2** codebase plus the seven-card stud layer and the menu item view: every menu item now shows a **photo and a description** in the detail panel, with the data extended in `data/menu.csv`.

## Where things live

There are two entry points and a shared data layer:

| Entry point | Class | What it runs |
|---|---|---|
| `edu.utsa.cs3443.goodburger.Launcher` | the app | the full GoodBurger app (sign-in → menu → checkout → profile → rewards page) |
| `rewards.RewardsApp` | the games | a standalone launcher for the mini-games only (used during development, supports `--preview`/`--smoke`) |

The two sides talk to each other in one place only: `RewardsPageController` (app side) hosts the games, and both sides share player progress through `rewards.ProgressStore`, which persists to a file in `data/`.

The app packages (`edu.utsa.cs3443.goodburger.*`) each carry a `package-info.java` that explains what belongs there and why:

- `…goodburger` — app shell: `Launcher`, `GoodBurgerApplication` (owns the window + `switchScreen`)
- `…goodburger.controller` — one FXML controller per page (UI wiring only)
- `…goodburger.model` — domain + data: `Session`, `AccountManager`, `MenuManager`, `Order*`, `DataManager`
- `…goodburger.view` / `…view.menuPanels` — shared components and the menu-screen panels
- `rewards` — the mini-games, shared progress, and scoring

For the full class-level picture (what calls what, and how data flows), see the architecture diagram in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). The UML design diagram submitted for this project is `goodburgeruml-1.jpg`.

## Run

### Linux — `./run-linux.sh`

```bash
./run-linux.sh
```

Requires a 64-bit JDK 21 or newer on `PATH` (or set `JAVA_HOME`). The script finds the app jar (`windows-runtime/goodburger.jar`, or `target/` after a build, building if neither exists) and the OpenJFX 21.0.6 **Linux** jars Maven downloaded into `~/.m2` (any `./build.sh` run populates them). Point `GOODBURGER_JAVAFX_DIR` at a folder of Linux JavaFX jars to override.

### Windows — `run-windows.bat`

Extract the entire ZIP and double-click `run-windows.bat`. The package includes the compiled app and the Windows x64 JavaFX 21.0.6 libraries; it uses an installed 64-bit Java 21 or newer.

### Development (Maven)

JDK 21 or newer:

```bash
./build.sh    # mvn clean package
./test.sh     # compile and run the logic checks (rewards.LogicTest)
```

## Menu data and the item view

Items come from `data/menu.csv`:

```
id,name,category,price,calories,protein,carbs,fat,sodium,cateringOnly,photo,description
```

- `photo` — file name of the item's image in `src/main/resources/images/`. Empty means no photo.
- `description` — free text, wrapped in double quotes; it may contain commas. The CSV parser is quote-aware (a `""` inside a quoted field is a literal quote). Legacy rows without the last two columns still parse, with empty photo and description.

The detail panel (`ItemDetailPanel`) renders each item with a **normalized photo** and the description:

- The photo box is a fixed 220×220. The image is scaled to *cover* the box (`scale = max(220/w, 220/h)`), centered, and trimmed with a rounded-rectangle clip — so photos of any original size render at the same size with no distortion.
- A missing or empty photo shows the placeholder background (`.item-photo` in `styles/menuScreen.css`) instead of crashing.
- Rendered results: [`previews/menu-item-photo.png`](previews/menu-item-photo.png) (Rowdy Burger) and [`previews/menu-item-no-photo.png`](previews/menu-item-no-photo.png) (Ice Cream, which has no photo).

## Seven-card stud (Poker button)

Both sides are dealt seven cards: two stay hidden, five are shown one per street, and you bet, call, raise, or fold on each of six streets. The best five of the seven cards wins the pot at showdown. Shown ranks that match the dealer's push the pot. The hand runs on a 50-point ante against your rewards balance; the net win or loss is settled through `ProgressStore.adjustPoints`, and completing a winning showdown can claim the daily bonus. The first poker win earns the one-time Card Shark award of 200 points. The dealer follows a fixed strategy (check-raises strong hands, limps early junk, and folds junk by the third street) so every result is deterministic for a given deal.

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

## Design

The [Figma design](https://www.figma.com/design/4qoghK6Hi24ovxvCw7Nmnl/Rewards---Pipe-Flight---JavaFX-UI) contains the original editable vector rewards layout alongside screenshots of the JavaFX rewards screen, all three game stages, poker, upgrades, and the ending.
