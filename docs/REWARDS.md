# How rewards points work

This is the plain-language explanation of the points flow, for the demo video and
for anyone testing the branch. It describes what `poker-v2` does.

## The rule: the user file is the source of truth

Every account's points live in `data/users.csv` (last column of each row). The
rewards page and the profile page both show that number. The rewards games
(poker, Pipe Flight) spend and earn *that* number, and every change is written
back to the user's row, so the number you see on screen and the number in the
file are always the same.

```
data/users.csv row for the signed-in account
        |
        |  sign in:  points column  -->  rewards page balance
        |
        |  every change (poker result, redemption, ...)
        v
        the row in users.csv is rewritten with the new total
```

## What happens, step by step

1. **Sign in.** `SignInController` logs the user in through `AccountManager`;
   `Session` now holds the user, including the points read from `users.csv`.
2. **Open the rewards page.** `RewardsPageController.initialize()` reads the
   signed-in user's points from the session and calls `ProgressStore.setBalance`
   with them. The page's "Current rewards points" label shows exactly the
   `users.csv` value. (Before this change the page opened a shared default
   progress file that nobody seeded, which is why preset accounts showed 0.)
3. **Play.** Poker and Pipe Flight run against the same `ProgressStore` the page
   uses. A settled hand, a redemption, or a game win changes the balance.
4. **Write-back.** `ProgressStore` fires `onBalanceChanged` after every change;
   the `UserRewardStore` hook (shared by the rewards page and the checkout
   page) rewrites the user's row in `users.csv` with the new total. The
   profile page, which reads points through the same `Session`/`AccountManager`
   path, shows the same number.
5. **Sign out, sign back in.** The new login reads `users.csv` again, so the
   points survived. Nothing is lost between sessions.
6. **Redeem, then checkout.** Redeeming on the rewards page deducts points and
   adds the item to the shared order as a free line (soda is the Soft Drink,
   fries are the French Fries, salad is the Side Salad, all $0.00). It shows
   under the Current Order at checkout and in the cart on the menu page, the
   same way a menu item does. Opening checkout re-syncs the order from the
   per-user store, so the lines agree even after an app restart. The "Rewards:"
   block under the total shows the current point balance; "Trade in" returns
   the item for half its cost in points, re-syncs `users.csv`, and removes the
   line from the order.

## Where each piece of state lives

| State | Where | Why |
|---|---|---|
| Account points (the balance) | `data/users.csv`, last column | visible on profile + rewards; survives everything |
| Per-user game state (daily cap, streak, achievements, redemptions) | `~/.local/share/rewards-javafx/progress-<userId>.properties` | one file per account, so two users on the same machine never mix state |
| Guest state | `~/.local/share/rewards-javafx/progress.properties` (shared default) | guests have no user file row, so nothing is written to `users.csv` for them |
| Redeemed food items | the same per-user progress file (`progress-<userId>.properties`) | each redemption also lands in the shared order as a $0.00 line (`UserRewardStore.syncRewardToCart`), so it shows under the Current Order at checkout; trade-in refunds half the cost in points and removes the line |

`rewards.progress.path` (JVM property) still overrides the file location; the
standalone `rewards.RewardsApp` keeps using the default file, which is why the
app-side and standalone-side stay in sync on a machine that runs both.

## What to show on camera (3-minute script)

1. Open `data/users.csv` in an editor, next to the app. Point at the demo
   account's points (e.g. `test.test@gmail.com` / `test1`).
2. Sign in as that account. Open the rewards page: the balance label matches
   the file exactly. Note that Pipe Flight is not on the page; Poker is.
3. Play one poker hand (fold or showdown). The balance moves.
4. Switch back to the editor: the row in `users.csv` now shows the new total.
   Open the profile page too: same number.
5. Sign out, sign back in: the balance is still the new total, straight from
   the file.
6. Redeem an item on the rewards page (e.g. the 300-point soda). Open the
   checkout page: the soda appears under the Current Order as a free $0.00
   line, right where menu items go. The "Rewards:" block below the total shows
   the new lower point balance. Press Trade in: +150 pts refund, the line is
   gone from the order, and the `users.csv` row tracks the whole way.

## Verification

- `./test.sh` covers the economy rules and the ProgressStore file format.
- The points-sync path was driven end to end headlessly on 2026-11-21
  (38 checks): sign in with the test account (5000 pts), load the page
  (label showed 5000), fold one poker hand, `users.csv` updated to 4950,
  re-login reads 4950 back; then redeem the soda (4650), the free line
  lands in the shared order, the checkout cart (the same list the order
  uses) shows it under the Current Order with the 4650 balance, and trading
  it in at checkout
  refunds 150 (4800) and removes the item, with `users.csv` mirroring each
  step.
