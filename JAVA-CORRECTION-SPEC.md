# GoodBurger (Rewards) — Java model correction spec (2026-11-02)

The CS3443 "Project Data & Code Design" got 75/100. The corrected data + UML
already exist; THIS spec makes the Java app actually implement that corrected
model. Work in THIS directory (`/home/fabri/Downloads/Erome/RewardsRun/RewardsJavaFX`).
Everything below must be Java (same project, same package `rewards`), must build
with `./build.sh`, and must pass `./test.sh` (existing `LogicTest` must stay green).

## Inputs already on disk (do not edit — read them)
- `src/main/resources/goodburger/*.csv` — the corrected 6-file data set
  (menu, modifiers, orders, orders_items, users, favoriteorders). Exact headers:
  - menu.csv: `id,name,category,price,calories,protein,carbs,fat,sodium,cateringOnly`
  - modifiers.csv: `itemId,name,extraPrice,extraCalories`
  - orders.csv: `orderId,customerId,orderType,total,tax,pickupTime`
  - orders_items.csv: `orderId,itemId,quantity,modifierNames` (semicolon-separated, may be empty)
  - users.csv: `id,name,birthday,email,password,points` (password = SHA-256 hex)
  - favoriteorders.csv: `userId,orderId,addedAt`
- Reference UML (the model to implement): `/home/fabri/Documents/classes/cs3443/goodburger-pcd-fix-2026-11-02/diagram/goodburger-uml.puml`
- Reference persistence pattern (the TA's example): `/home/fabri/Documents/classes/cs3443/mzu955_lab4/src/edu/utsa/cs3443/mzu955_lab4/model/AidShipManager.java`

## What to implement (new classes, all in package `rewards`)

### 1. Model classes (pure data + logic, NO JavaFX imports, NO file I/O)
- `MenuItem` — id, name, category (enum Category {ENTREE, SIDE, DRINK, CATERING}),
  price, calories, protein, carbs, fat, sodium, cateringOnly, `List<Modifier> modifiers`.
- `Modifier` — itemId, name, extraPrice, extraCalories.
- `Order` — orderId, customerId, orderType (enum OrderType {PICKUP, DINEIN, DELIVERY}),
  pickupTime, `List<OrderItem> items`. Methods:
  - `double subtotal()` = sum of line totals (see OrderItem).
  - `double tax()` and `double total()` — must reproduce orders.csv values when
    built from that CSV (orders.csv stores total and tax; verify O1001..O1010
    match by loading the CSV and comparing computed vs stored within 0.005).
  - `int totalCalories()` (and other nutrition sums) — computed by summing
    item + modifier contributions across all lines (NO stored per-order nutrition).
- `OrderItem` (order line) — itemId, quantity, `List<Modifier> modifiers`.
  - `double lineTotal(Menu menu)` = quantity * (item.price + sum(modifier.extraPrice)).
- `SavedOrder` — an Order wrapper with `orderId`, `addedAt` (what favoriteorders.csv
  references). (UML draws it as a generalization of Order; in Java a thin wrapper
  or subclass — pick one, document it in the class javadoc.)
- `User` — id, name, birthday, email, passwordHash, points. Methods:
  - `static String hashPassword(String password)` — SHA-256 via
    `java.security.MessageDigest`, lowercase hex, MUST produce the exact digests
    in users.csv for the known seed passwords (see test below).
  - `boolean checkPassword(String plaintext)` — `hashPassword(p).equals(passwordHash)`.

### 2. OrderManager (current-order lifecycle; UML FIX 2)
- Fields: `Order currentOrder`, `List<MenuItem> menu`.
- `Order newOrder(String customerId, OrderType type, String pickupTime)`
- `void addToOrder(String itemId, int quantity, List<Modifier> mods)`
- `double calculateTotal()` (delegates to currentOrder totals incl. tax rule of the CSV)
- `Order checkout()` — validates non-empty, returns the finished Order.
No file I/O here — persistence goes through DataManager.

### 3. DataManager (ALL file I/O; UML FIX 4 — the AidShipManager pattern)
- Constructor takes a base directory (Path) holding the 6 CSVs.
- Loads: `loadMenu() : List<MenuItem>`, `loadModifiers() : List<Modifier>` (attached
  to their MenuItem), `loadOrders() : List<Order>` (joined with their OrderItems from
  orders_items.csv), `loadUsers() : List<User>`, `loadFavoriteOrders() : List<SavedOrder>`.
- Saves: `saveOrder(Order o)` (appends to orders.csv + orders_items.csv),
  `saveUser(User u)`, `saveFavoriteOrder(String userId, Order o)`.
- Parsing: plain `BufferedReader`/`java.io` or `java.nio.file` line splitting;
  a small CSV helper is fine (data has no quoted commas). No third-party libs.
- ONLY DataManager opens the 6 CSVs. No other class may open them. (Enforced by
  a grep, see VERIFY — the app code may reference the resource dir name, but the
  only class that constructs File/Path readers against those files is DataManager.)

### 4. Wiring into the app (RewardsApp)
- On startup, build a `DataManager` pointed at the bundled
  `src/main/resources/goodburger` directory (resolve it from the classpath at
  build time or from the working dir; prefer a small `resourceDir()` helper —
  the app must run both from the jar (run.sh) and from the test harness with a
  real directory).
- The Menu/Checkout screens get their items through DataManager/OrderManager,
  not through inline mock lists. Keep the existing look (colors, layout, nav)
  — this is a model correction, not a redesign.

### 5. Tests (extend `src/test/java/rewards/LogicTest.java`, same style — no JUnit,
  throw AssertionError on failure, print "All ... checks passed." at the end)
Add a `goodburger()` section that:
1. Loads the 6 CSVs from a temp copy of `src/main/resources/goodburger`.
2. menu: 12 items, header fields parsed, all nutrition ints present (no hex anywhere).
3. orders: for ALL 10 orders, recompute line sums from orders_items + menu +
   modifiers and assert each equals the stored total within 0.005 (print the
   per-order check table: orderId, stored, computed, OK).
4. hashPassword: assert `User.hashPassword` reproduces at least the exact hex
   digests stored in users.csv. Seed passwords (user: password) are:
   001 alex.smith@gmail.com → "password1" ... if that fails, derive the correct
   seed passwords yourself by trial (common: "password", "password1", "<name>",
   "<email>") until `hashPassword(seed) == stored hash` for user 001, then lock
   the assertion to whatever you verified. If you cannot find plaintext seeds,
   assert instead that hashPassword("known") is deterministic + 64 lowercase hex
   AND that checkPassword works on a freshly saved user — then state this in
   your final report.
5. OrderManager round-trip: newOrder → addToOrder (with modifiers) → checkout →
   saveOrder → reload → totals and lines survive.
6. DataManager is the only class with file access (grep rule, below).

## Hard constraints (VERIFY — all must hold before you call it done)
- `./build.sh` exits 0; `./test.sh` exits 0 (old LogicTest assertions still pass —
  you may ADD sections, not remove old ones).
- `grep -rn "new File(\|BufferedReader\|Files.readAllLines\|Files.newBufferedReader" src/main/java/rewards/ | grep -v DataManager` →
  no hits outside DataManager for the goodburger CSV paths (the existing
  ProgressStore file access is separate and may stay).
- `grep -rn "0A73F29" src/` → no hex nutrition anywhere in code or resources.
- No JavaFX import in any of the new model classes (MenuItem, Modifier, Order,
  OrderItem, SavedOrder, User, OrderManager, DataManager).
- Update `README.md` with a short "GoodBurger model" section: the 6 CSVs,
  DataManager/OrderManager responsibilities, hashPassword, and the
  per-order nutrition derivation (not stored).
- Finish with: build.sh + test.sh output, the per-order sum table, and the list
  of files you added/changed.
