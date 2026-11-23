package edu.utsa.cs3443.goodburger;

import javafx.application.Application;

/**
 * Plain main class used to start the app from IntelliJ without module setup.
 * It hands off to GoodBurgerApplication, which is the real JavaFX application.
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(GoodBurgerApplication.class, args);
    }
}
