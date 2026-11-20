# Data files (data/)

The app reads and writes four CSV files from `data/`. Paths are resolved from the
**working directory the app was launched from**: the run scripts cd to the repo root
first, so running them "just works". If you launch the app from an IDE with a
different working directory, the app will look for (or create) `data/` there instead.

The parsers read rows **by column position, not by header name** (see
`DataManager.parseItem`/`parseModifier`). The header line is for humans: you can
rename a header (or leave the stray space in ` extraProtein` in modifiers.csv) and
nothing breaks; reordering or deleting a column does.

## menu.csv: 12 columns

`id,name,category,price,calories,protein,carbs,fat,sodium,cateringOnly,photo,description`

- **id**: `Bxx` entrees, `Sxx` sides, `Dxx` drinks, `DDxx` desserts. `B01` = Rowdy Burger.
- **category**: `entrees`, `sides`, `drinks` or `desserts` (matched case-insensitively
  against `Category`).
- **price**: dollars, e.g. `6.99`.
- **cateringOnly**: `true`/`false`.
- **photo**: file name in `src/main/resources/images/`, loaded as a classpath resource.
  Photos are `.jpg` (converted from PNG in Nov 2026); keep new photos `.jpg` too.
  Blank = the grey placeholder box in the item view.
- **description**: last column, so it may contain commas; descriptions with commas are
  quoted, and the parser honours quotes.

A legacy 10-column row (no photo/description) is still accepted by the parser, which
is why old menu rows keep working.

## modifiers.csv: 8 columns

`itemId,name,extraPrice,extraCalories, extraProtein, extraCarbs, extraFat, extraSodium`

Each row is one add-on checkbox for the item `itemId` names. Extra price and nutrition
are per unit and multiply by the quantity (see `OrderItem.getLinePrice`). A row that
points at an unknown itemId is skipped silently; `ModelTest.menuIntegrity` catches it.

## users.csv: 6 columns

`id,name,birthday,email,password,points`

Passwords are stored **plain text** (class project, not a security boundary). The
seeded rows are demo accounts; registration appends a row with the next sequential id.
`test.test@gmail.com` / `test1` is the low-friction demo login.

## orders.csv: write-only

The app appends one row per checkout; nothing re-reads it into the UI. Appended rows
follow the header `orderId,customerId,orderType,pickupTime,subtotal,tax,total,calories,items`.
The `items` field encodes `B01x2(Add Bacon/No onions);S01x1`: itemId x quantity (chosen
add-ons), joined with semicolons. Order ids are `O` + (highest existing + 1), starting
at `O1001`.

The two seeded rows (O1001, O1002) are legacy 7-column rows from an earlier format
(`total,tax,nutrition` where the 9-column format writes `subtotal,tax,...,calories`,
and order types `PICKUP`/`DINEIN` that `OrderType` no longer defines). They are kept
for demo continuity; nothing parses them back. `DataManager` carries a TODO about the
old nutrition column.

## Adding a menu item (copy-paste recipe)

1. Put the photo in `src/main/resources/images/` as a `.jpg` (opaque, any size; the
   item view crops it into a 220x220 box).
2. Append to `data/menu.csv`:
   `B05,New Burger,entrees,7.49,850,40,60,45,900,false,02B_NewBurger.jpg,"Description, with a comma is fine when quoted"`
3. Optionally append add-ons to `data/modifiers.csv` with itemId `B05`.
4. `./build.sh && ./test.sh` - `ModelTest.menuIntegrity` fails if the photo name,
   category, id or column count is wrong.
