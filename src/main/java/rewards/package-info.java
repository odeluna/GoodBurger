/**
 * The rewards economy: seven-card stud Poker and its shared progress.
 *
 * <p>The app-side integration lives in
 * edu.utsa.cs3443.goodburger.controller.RewardsPageController, which hosts the
 * poker table inside the rewards page and shows the balance and redemptions.
 * Player progress (points, achievements) is shared through ProgressStore, which
 * persists to a file in the data/ folder.
 *
 * <p>Internals: Poker / PokerWindow (seven-card stud), EconomyRules (scoring),
 * Achievements (unlockable rewards).
 */
package rewards;
