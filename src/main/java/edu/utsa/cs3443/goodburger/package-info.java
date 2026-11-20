/**
 * App shell and entry points.
 *
 * <p>There are TWO entry points in this repository:
 * <ul>
 *   <li>{@link Launcher} - the full GoodBurger app (sign in, menu, checkout, profile).
 *       This is what run-linux.sh and run-windows.bat run.</li>
 *   <li>{@code rewards.RewardsApp} - a standalone main() that runs only the rewards
 *       mini-games, used during development (it understands the --preview and --smoke flags).</li>
 * </ul>
 *
 * <p>{@link GoodBurgerApplication} owns the window and the switchScreen() page swap
 * that every controller uses to move between FXML pages.
 */
package edu.utsa.cs3443.goodburger;
