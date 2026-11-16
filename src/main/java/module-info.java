/**
 * Module description for the Good Burger app.
 *
 * <p>JavaFX 11+ ships as Java modules, so the app declares which JavaFX modules it
 * uses ({@code requires}) and which of its own packages other code may see
 * ({@code exports}). JavaFX must be able to reach {@code com.goodburger} because it
 * creates {@link com.goodburger.GoodBurgerApp} by reflection when the app starts.</p>
 */
module com.goodburger {
    requires javafx.controls;
    requires java.desktop;

    exports com.goodburger;
    exports com.goodburger.model;
    exports com.goodburger.data;
    exports com.goodburger.order;
    exports com.goodburger.ui;
    exports com.goodburger.ui.menu;
}
