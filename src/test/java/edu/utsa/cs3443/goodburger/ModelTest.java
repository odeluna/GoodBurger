package edu.utsa.cs3443.goodburger;

import edu.utsa.cs3443.goodburger.model.AccountManager;
import edu.utsa.cs3443.goodburger.model.Category;
import edu.utsa.cs3443.goodburger.model.DataManager;
import edu.utsa.cs3443.goodburger.model.MenuManager;
import edu.utsa.cs3443.goodburger.model.MenuItem;
import edu.utsa.cs3443.goodburger.model.Modifier;
import edu.utsa.cs3443.goodburger.model.Order;
import edu.utsa.cs3443.goodburger.model.OrderItem;
import edu.utsa.cs3443.goodburger.model.User;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dependency-free checks for the GoodBurger model layer.
 *
 * <p>Covers the three ways the model breaks in practice:
 * <ul>
 *   <li>menu data that no longer matches the files (a row skipped, a photo name
 *       pointing at a deleted image, a modifier line attached to nothing);</li>
 *   <li>account round trips (log in, register, duplicate e-mail, delete);</li>
 *   <li>order math and the order-id sequence (line price, subtotal, O####).</li>
 * </ul>
 *
 * <p>Same style as {@code rewards.LogicTest}: plain {@code main()}, no JUnit, first
 * failure throws. Runs via {@code ./test.sh} from the repo root; reads the real
 * {@code data/} files but writes only into temp directories.</p>
 */
public final class ModelTest {

    private static int passed = 0;

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        passed++;
    }

    public static void main(String[] args) throws Exception {
        menuIntegrity();
        userRoundTrip();
        orderRoundTrip();
        System.out.println("ModelTest: all " + passed + " model checks passed.");
    }

    /** data/menu.csv, data/modifiers.csv and the classpath photos must agree with each other. */
    static void menuIntegrity() throws Exception {
        DataManager dm = new DataManager(Path.of("data"));
        MenuManager menu = dm.loadMenu();
        List<MenuItem> items = menu.getAllItems();
        int menuRows = Files.readAllLines(Path.of("data/menu.csv")).size() - 1;   // minus the header
        check(items.size() == menuRows,
                "loader dropped rows: " + items.size() + " items vs " + menuRows + " menu.csv rows");

        int attached = 0;
        for (MenuItem item : items) {
            check(item.getId() != null && !item.getId().isBlank(), "item with blank id");
            check(item.getName() != null && !item.getName().isBlank(), "item with blank name: " + item.getId());
            check(item.getPrice() > 0, "non-positive price: " + item.getId());
            check(item.getCategory() != null, "null category: " + item.getId());
            if (item.getPhoto() != null && !item.getPhoto().isBlank()) {
                check(ModelTest.class.getResource("/images/" + item.getPhoto()) != null,
                        "photo named in menu.csv is missing from the classpath: " + item.getPhoto());
            }
            attached += item.getModifiers().size();
        }

        int modifierRows = Files.readAllLines(Path.of("data/modifiers.csv")).size() - 1;
        check(attached == modifierRows,
                "modifier rows attached (" + attached + ") != modifiers.csv rows (" + modifierRows
                        + "); a row points at an unknown item id or was skipped");

        // Category filters must partition the whole menu (no item lost in a filter).
        int byCategory = 0;
        for (Category c : Category.values()) {
            byCategory += menu.getByCategory(c).size();
        }
        check(byCategory == items.size(), "getByCategory does not partition the menu");
    }

    /** Log in, register, reject a duplicate, delete. Works on a temp users.csv, never the seeded one. */
    static void userRoundTrip() throws Exception {
        Path tmp = Files.createTempDirectory("gbg-modeltest-users");
        Files.writeString(tmp.resolve("users.csv"),
                "id,name,birthday,email,password,points\n"
              + "001,Alex Smith,05/14/2003,alex.smith@gmail.com,secret123,1186\n");

        AccountManager am = new AccountManager(new DataManager(tmp));
        check(am.login("alex.smith@gmail.com", "secret123"), "seeded user should log in");
        check(!am.login("alex.smith@gmail.com", "wrong-password"), "wrong password should be rejected");
        check(am.findUser("alex.smith@gmail.com").getPoints() == 1186, "points not preserved from file");

        check(am.registerUser("New Person", "01/01/2020", "new.person@gmail.com", "pw-12345"),
                "valid registration should succeed");
        check(!am.registerUser("Another Person", "02/02/2020", "NEW.PERSON@GMAIL.COM", "other-pw"),
                "duplicate e-mail (case-insensitive) should be rejected");
        check(Files.readAllLines(tmp.resolve("users.csv")).size() == 3,
                "users.csv should now hold the header plus two users");

        User removed = am.findUser("new.person@gmail.com");
        check(am.deleteAccount(removed), "deleteAccount should succeed");
        check(!am.login("new.person@gmail.com", "pw-12345"), "deleted user should not log in");
        check(am.findUser("alex.smith@gmail.com") != null, "other users must survive a delete");
    }

    /** Line price, subtotal, order-id sequence and the orders.csv write. */
    static void orderRoundTrip() throws Exception {
        MenuManager menu = new DataManager(Path.of("data")).loadMenu();
        MenuItem burger = menu.findById("B01");
        check(burger != null, "B01 (Rowdy Burger) must exist in menu.csv");
        Modifier bacon = null;
        for (Modifier m : burger.getModifiers()) {
            if (m.toString().startsWith("Add Bacon")) {
                bacon = m;
            }
        }
        check(bacon != null, "B01 must offer an Add Bacon modifier");

        OrderItem line = new OrderItem(burger);
        line.setQuantity(2);
        line.toggleModifier(bacon);
        double expectedLine = (burger.getPrice() + bacon.getExtraPrice()) * 2;
        check(Math.abs(line.getLinePrice() - expectedLine) < 0.001,
                "line price must be (base + add-ons) x quantity");
        int expectedCalories = 2 * (burger.getNutrition().getCalories() + bacon.getNutrition().getCalories());
        check(line.getNutrition().getCalories() == expectedCalories,
                "line nutrition must scale with quantity and add-ons");

        DataManager dm = new DataManager(Files.createTempDirectory("gbg-modeltest-orders"));
        Order order = new Order(dm.nextOrderId(), "model-test");
        order.addItem(line);
        check(order.getOrderId().equals("O1001"), "fresh order file must start at O1001, got " + order.getOrderId());
        check(Math.abs(order.getSubtotal() - expectedLine) < 0.001, "order subtotal math wrong");

        double tax = order.getSubtotal() * 0.0825;
        dm.saveOrder(order, order.getSubtotal(), tax, order.getSubtotal() + tax);
        List<String> rows = Files.readAllLines(dm.getOrdersFile());
        check(rows.size() == 2 && rows.get(1).startsWith(order.getOrderId() + ","),
                "orders.csv must gain exactly one row for the saved order");
        check(rows.get(1).contains("B01x2"), "items field must encode itemId x quantity");
        check(dm.nextOrderId().equals("O1002"), "nextOrderId must follow the saved order");
    }

    private ModelTest() {}
}
