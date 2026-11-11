package com.goodburger.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The bar along the bottom of the app with one button per main screen: Main, Menu, Rewards, Checkout.
 *
 * <p><b>Why it lives in {@code ui} and not {@code ui.menu}:</b> the bar belongs to the whole app, not to
 * the food menu. It stays on screen while the area above it switches between screens, so
 * {@link com.goodburger.GoodBurgerApp} places it in the bottom slot of the app's outer layout, and every
 * teammate's screen goes in the center slot above it.</p>
 *
 * <p><b>Current behavior:</b> only the Menu screen exists so far, so pressing a button just marks it as the
 * selected one. When teammates' screens are ready, {@link #setOnSelect} is the single hook for real
 * navigation: the app passes a function that swaps the center screen based on the button's name.</p>
 */
public class NavBar extends HBox {

    /** The four screens, in the order the buttons appear. */
    public static final String[] SCREENS = {"Main", "Menu", "Rewards", "Checkout"};

    /** Style for the button of the screen you're on: the purple from the Figma design, with white text. */
    private static final String SELECTED_STYLE = "-fx-background-color: #4b3b78; -fx-text-fill: white;";
    private static final String NORMAL_STYLE = "-fx-background-color: #8a8399; -fx-text-fill: white;";

    // LinkedHashMap keeps the buttons in insertion order, so looping over it matches the bar left to right.
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private java.util.function.Consumer<String> onSelect = name -> { };   // does nothing until the app sets one

    /** @param initialScreen the screen the app opens on; its button starts highlighted */
    public NavBar(String initialScreen) {
        setSpacing(2);
        setPadding(new Insets(2, 0, 0, 0));
        for (String name : SCREENS) {
            Button b = new Button(name.toLowerCase());   // lower case labels like the Figma design
            b.setMaxWidth(Double.MAX_VALUE);
            b.setPrefHeight(45);
            HBox.setHgrow(b, Priority.ALWAYS);           // all four buttons share the width equally
            b.setOnAction(e -> select(name));
            buttons.put(name, b);
            getChildren().add(b);
        }
        highlight(initialScreen);
    }

    /**
     * Sets what happens when a button is pressed. Not needed yet; this is where screen switching will plug in.
     *
     * @param onSelect called with the screen name ("Main", "Menu", "Rewards" or "Checkout")
     */
    public void setOnSelect(java.util.function.Consumer<String> onSelect) {
        this.onSelect = onSelect;
    }

    /** Highlights the pressed button and reports it. */
    private void select(String name) {
        highlight(name);
        onSelect.accept(name);
    }

    /** Gives the named button the selected style and every other button the normal style. */
    private void highlight(String name) {
        buttons.forEach((screen, b) -> b.setStyle(screen.equals(name) ? SELECTED_STYLE : NORMAL_STYLE));
    }
}
