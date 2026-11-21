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
   the controller's `syncUserPoints` rewrites the user's row in `users.csv`
   with the new total. The profile page, which reads points through the same
   `Session`/`AccountManager` path, shows the same number.
5. **Sign out, sign back in.** The new login reads `users.csv` again, so the
   points survived. Nothing is lost between sessions.

## Where each piece of state lives

| State | Where | Why |
|---|---|---|
| Account points (the balance) | `data/users.csv`, last column | visible on profile + rewards; survives everything |
| Per-user game state (daily cap, streak, achievements, redemptions) | `~/.local/share/rewards-javafx/progress-<userId>.properties` | one file per account, so two users on the same machine never mix state |
| Guest state | `~/.local/share/rewards-javafx/progress.properties` (shared default) | guests have no user file row, so nothing is written to `users.csv` for them |
| Redeemed food items | in-memory session, listed in Checkout | trade-in happens at checkout for half the points |

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

## Verification

- `./test.sh` covers the economy rules and the ProgressStore file format.
- The points-sync path (login -> page -> fold -> csv write-back -> re-login)
  was driven end to end headlessly on 2026-11-21: sign in with the test
  account (5000 pts), load the page (label showed 5000), fold one poker hand,
  `users.csv` updated to 4950, and a sign-out/sign-in read 4950 back.
