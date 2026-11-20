# GoodBurger — architecture

What actually runs, how it fits together, and how data flows. The UML class
diagram for this design is [`goodburgeruml-1.jpg`](goodburgeruml-1.jpg) (as
submitted for the Project Data and Code Design assignment); this file is the
text companion to it.

## Two entry points

```
Launcher.main()                        rewards.RewardsApp.main()
   |  (what run-linux.sh /                 |  (standalone, development only;
   |   run-windows.bat run)                |   understands --preview / --smoke)
   v                                        v
GoodBurgerApplication              PipeFlightGame / PokerWindow
   |  start():                        (the mini-games, no restaurant UI)
   |  Session.get().loadMenu()
   |  loads home_page.fxml
   v
the app (7 FXML pages, one controller each)
```

## App pages and controllers

Every page is an FXML file in `src/main/resources/edu/utsa/cs3443/goodburger/`
that names its controller with `fx:controller`. Navigation is a single static
method: `GoodBurgerApplication.switchScreen(source, fxmlFile)` swaps the scene.

```
sign_in_page    -> SignInController       (AccountManager.login -> Session)
register_page   -> RegisterController     (AccountManager.registerUser)
home_page       -> HomeScreenController   (menu / rewards / profile buttons)
menu_page       -> MenuScreenController   (view.menuPanels: MenuScreen,
                                           ItemGridPanel, ItemDetailPanel,
                                           ItemPreviewPanel, CategoryPanel)
checkout_page   -> CheckoutScreenController (Order/OrderManager -> orders.csv)
profile_page    -> ProfileController      (session user, points, delete account)
rewards_page    -> RewardsPageController  <-- the only app class that reaches
                                             into rewards.* (hosts the games)
```

## Model layer (single source of truth)

```
Session (static, one per app run)
  +-- currentUser (User)            <- who is signed in; AccountManager reads it
  +-- MenuManager (menu items)      <- loaded once in GoodBurgerApplication.start
  +-- WIDTH / HEIGHT

DataManager
  +-- data/users.csv    (id,name,birthday,email,password,points)
  +-- data/menu.csv     (12 columns incl. photo,description)
  +-- data/orders.csv
  quote-aware CSV parsing lives here; controllers never touch files directly
```

## Rewards side (rewards.*)

```
RewardsPageController (app)  <---->  rewards.*
   |  hosts the game scenes        |
   |                               +-- PipeFlightGame / PipeFlightCanvas /
   |                                   SpriteAtlas / GameAudio
   |                               +-- Poker (seven-card stud) / PokerWindow
   |                               +-- RewardGameDriver, EconomyRules (scoring)
   |                               +-- Achievements (unlockables)
   +----- shared progress ---------+
              ProgressStore (persists points/achievements to a file in data/,
              so app-side and standalone-side progress stay in sync)
```

## Data flow, one order end to end

```
menu_page: user picks an item
  -> MenuScreenController adds it to the cart (OrderManager, in Session)
checkout_page: CheckoutScreenController
  -> OrderManager completes the order
  -> DataManager.saveOrder() appends a row to data/orders.csv
profile_page: ProfileController reads points back through Session/AccountManager
```

## Conventions

- Controllers do UI wiring only; business rules live in `model/` (app) or
  `rewards/` (games).
- `data/` is the only place state is persisted; `DataManager` owns it.
- Standalone `rewards.RewardsApp` never instantiates app controllers or FXML;
  the bridge is exactly `RewardsPageController` + `ProgressStore`.
