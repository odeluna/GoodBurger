/**
 * One JavaFX controller per FXML page; each FXML names its controller with fx:controller.
 *
 * <p>Controllers are UI wiring only: they read the form input, call into model/
 * managers, then swap to the next page with GoodBurgerApplication.switchScreen().
 * Business rules live in the model classes, not here.
 *
 * <p>RewardsPageController is the only class in this package that reaches into
 * rewards.* - it hosts the mini-games inside the app and shares player progress
 * with them through rewards.ProgressStore.
 */
package edu.utsa.cs3443.goodburger.controller;
