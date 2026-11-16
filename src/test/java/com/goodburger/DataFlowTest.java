package com.goodburger;

import com.goodburger.data.MenuFileReader;
import com.goodburger.model.Category;
import com.goodburger.model.Menu;
import com.goodburger.model.MenuItem;
import com.goodburger.model.Modifier;
import com.goodburger.model.OrderItem;
import com.goodburger.order.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Follows the sample data through every class, in the same order the app uses them:
 * CSV files, then {@link MenuFileReader}, then {@link Menu}, then {@link OrderItem}, then {@link Order}.
 *
 * <p>It reads the real files in the project's {@code data} folder, so if someone edits them and breaks
 * the format, this test fails with the file name and line number. Run it from IntelliJ with the green
 * arrow next to the class name, or with {@code mvn test}.</p>
 *
 * <p>Expected values come from the sample files:</p>
 * <pre>
 * 22 items: 8 entrees (B01-B08), 4 sides, 6 drinks, 3 desserts, plus 1 catering-only entree (C01)
 * B01 Classic Burger  ENTREE  $6.99  650 cal   add-ons: No onions, Add bacon (+$1.50, +120 cal), Add cheese
 * S01 Fries           SIDE    $2.49  320 cal   add-ons: Cheese sauce (+$0.75, +90 cal), Large size
 * C01 Party Tray      ENTREE  $59.99           catering only, no add-ons
 * </pre>
 */
class DataFlowTest {

    private Menu menu;

    @BeforeEach
    void loadSampleFiles() throws Exception {
        menu = new MenuFileReader().load(Path.of("data", "menu.csv"), Path.of("data", "modifiers.csv"));
    }

    @Test
    void readerLoadsEveryItemAndAttachesAddOnsToTheRightItem() {
        assertEquals(22, menu.getAllItems().size());
        assertEquals(3, menu.findById("B01").getModifiers().size());
        assertEquals(2, menu.findById("S01").getModifiers().size());
        assertEquals(0, menu.findById("C01").getModifiers().size());
        assertNull(menu.findById("NOPE"));
    }

    @Test
    void regularMenuHidesCateringItemsAndCateringMenuShowsOnlyThem() {
        // C01 is also an ENTREE, but catering-only, so the Entrees button shouldn't show it.
        assertEquals(8, menu.getByCategory(Category.ENTREE).size());
        assertTrue(menu.getByCategory(Category.ENTREE).stream().noneMatch(MenuItem::isCateringOnly));
        assertEquals("B01", menu.getByCategory(Category.ENTREE).get(0).getId());   // file order is kept
        assertEquals(4, menu.getByCategory(Category.SIDE).size());
        assertEquals(6, menu.getByCategory(Category.DRINK).size());
        assertEquals(3, menu.getByCategory(Category.DESSERT).size());
        assertEquals(1, menu.getCateringItems().size());
        assertEquals("C01", menu.getCateringItems().get(0).getId());
    }

    @Test
    void customizingAnItemUpdatesPriceAndCalories() {
        MenuItem burger = menu.findById("B01");
        Modifier bacon = burger.getModifiers().get(1);   // "Add bacon"
        OrderItem draft = new OrderItem(burger);

        assertEquals(6.99, draft.getLinePrice(), 0.001);

        draft.toggleModifier(bacon);                      // tick
        assertEquals(8.49, draft.getLinePrice(), 0.001);  // 6.99 + 1.50
        assertEquals(770, draft.getNutrition().getCalories());

        draft.setQuantity(2);
        assertEquals(16.98, draft.getLinePrice(), 0.001);
        assertEquals(1540, draft.getNutrition().getCalories());

        draft.toggleModifier(bacon);                      // untick
        assertEquals(13.98, draft.getLinePrice(), 0.001);
    }

    @Test
    void addOnsFromAnotherItemAreRejected() {
        OrderItem fries = new OrderItem(menu.findById("S01"));
        Modifier bacon = menu.findById("B01").getModifiers().get(1);
        assertThrows(IllegalArgumentException.class, () -> fries.toggleModifier(bacon));
        assertThrows(IllegalArgumentException.class, () -> fries.setQuantity(0));
    }

    @Test
    void finishedItemsEndUpInTheOrder() {
        Order order = new Order();
        OrderItem burger = new OrderItem(menu.findById("B01"));
        OrderItem fries = new OrderItem(menu.findById("S01"));
        fries.toggleModifier(fries.getItem().getModifiers().get(0));   // cheese sauce

        order.addItem(burger);
        order.addItem(fries);

        assertEquals(2, order.getItems().size());
        double subtotal = order.getItems().stream().mapToDouble(OrderItem::getLinePrice).sum();
        assertEquals(6.99 + 2.49 + 0.75, subtotal, 0.001);
    }
}
