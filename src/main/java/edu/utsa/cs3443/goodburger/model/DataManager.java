package edu.utsa.cs3443.goodburger.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The one class that reads and writes the files in the data folder
 * (menu.csv, modifiers.csv, users.csv and orders.csv).
 *
 * <p>Other classes ask DataManager for data instead of opening files themselves, so a change
 * to a file's location or format only has to be made here.</p>
 *
 * <p>The paths are relative, so they are looked up from the folder the app runs in. IntelliJ and
 * {@code mvn javafx:run} both run from the project folder, which is where {@code data} is.</p>
 */
public class DataManager {

    public static final String ORDERS_HEADER =
            "orderId,customerId,orderType,pickupTime,subtotal,tax,total,calories,items";

    private static final int MENU_COLUMNS = 10;
    private static final int MENU_COLUMNS_SHORT = 6;
    private static final int MODIFIER_COLUMNS = 4;

    private final Path dataFolder;

    public DataManager() {
        this(Path.of("data"));
    }

    /** Lets tests point the app at a different folder. */
    public DataManager(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    public Path getMenuFile()      { return dataFolder.resolve("menu.csv"); }
    public Path getModifiersFile() { return dataFolder.resolve("modifiers.csv"); }
    public Path getUsersFile()     { return dataFolder.resolve("users.csv"); }
    public Path getOrdersFile()    { return dataFolder.resolve("orders.csv"); }

    // ---------------------------------------------------------------- general file helpers

    public boolean exists(Path file) {
        return Files.exists(file);
    }

    /** Every line of a file, header included. */
    public List<String> readLines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    /** Replaces a file's contents, creating the folder if needed. */
    public void writeText(Path file, String contents) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, contents, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- menu

    /**
     * Reads menu.csv, then attaches each line of modifiers.csv to the item it names.
     * A bad line stops the load with a message that says which file and line.
     */
    public MenuManager loadMenu() throws IOException {
        Path menuPath = getMenuFile();
        Path modifiersPath = getModifiersFile();

        List<MenuItem> items = new ArrayList<>();
        List<String> menuLines = readLines(menuPath);
        for (int i = 1; i < menuLines.size(); i++) {           // i = 1 skips the header row
            String line = menuLines.get(i);
            if (line.isBlank()) continue;
            try {
                items.add(parseItem(line));
            } catch (RuntimeException e) {
                throw new IOException(menuPath.getFileName() + " line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        MenuManager menu = new MenuManager(items);

        List<String> modifierLines = readLines(modifiersPath);
        for (int i = 1; i < modifierLines.size(); i++) {
            String line = modifierLines.get(i);
            if (line.isBlank()) continue;
            String itemId = line.split(",")[0].trim();
            MenuItem owner = menu.findById(itemId);
            if (owner == null) {
                throw new IOException(modifiersPath.getFileName() + " line " + (i + 1)
                        + ": no menu item with id " + itemId);
            }
            try {
                owner.addModifier(parseModifier(line));
            } catch (RuntimeException e) {
                throw new IOException(modifiersPath.getFileName() + " line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return menu;
    }

    // menu.csv, either:
    //   id,name,category,price,calories,protein,carbs,fat,sodium,cateringOnly   (full nutrition)
    //   id,name,category,price,nutritionId,cateringOnly                           (what data/menu.csv has now)
    // The nutritionId format has no nutrition table yet, so those items get zero nutrition.
    MenuItem parseItem(String line) {
        String[] f = split(line, MENU_COLUMNS, MENU_COLUMNS_SHORT);
        Nutrition nutrition = Nutrition.ZERO;
        boolean catering;
        if (f.length == MENU_COLUMNS) {
            nutrition = new Nutrition(
                    Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                    Integer.parseInt(f[7]), Integer.parseInt(f[8]));
            catering = Boolean.parseBoolean(f[9]);
        } else {
            catering = Boolean.parseBoolean(f[5]);
        }
        return new MenuItem(
                f[0], f[1],
                Category.valueOf(f[2].toUpperCase()),
                Double.parseDouble(f[3]),
                nutrition,
                catering);
    }

    // modifiers.csv: itemId,name,extraPrice,extraCalories (or a nutritionId, which counts as 0 calories)
    Modifier parseModifier(String line) {
        String[] f = split(line, MODIFIER_COLUMNS);
        int calories;
        try {
            calories = Integer.parseInt(f[3]);
        } catch (NumberFormatException e) {
            calories = 0;
        }
        return new Modifier(f[1], Double.parseDouble(f[2]), calories);
    }

    private static String[] split(String line, int... allowedColumns) {
        String[] fields = line.split(",", -1);   // -1 keeps empty trailing fields so the count is honest
        boolean ok = false;
        for (int n : allowedColumns) {
            ok |= fields.length == n;
        }
        if (!ok) {
            throw new IllegalArgumentException("expected " + allowedColumns[0] + " columns but found "
                    + fields.length + " (names can't contain commas)");
        }
        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i].trim();
        }
        return fields;
    }

    // ---------------------------------------------------------------- orders

    /** The next free id, e.g. O1001, O1002, ... based on what orders.csv already holds. */
    public String nextOrderId() throws IOException {
        int highest = 1000;
        Path file = getOrdersFile();
        if (exists(file)) {
            List<String> lines = readLines(file);
            for (int i = 1; i < lines.size(); i++) {
                String id = lines.get(i).split(",")[0].trim();
                if (id.startsWith("O")) {
                    try {
                        highest = Math.max(highest, Integer.parseInt(id.substring(1)));
                    } catch (NumberFormatException ignored) {
                        // not one of our ids; skip it
                    }
                }
            }
        }
        return "O" + (highest + 1);
    }

    /**
     * Adds one line to orders.csv. Items are stored as {@code id x qty (modifiers)} joined by
     * semicolons, e.g. {@code B01x2(Add bacon/No onions);S01x1}.
     */
    public void saveOrder(Order order, double subtotal, double tax, double total) throws IOException {
        Path file = getOrdersFile();
        if (!exists(file) || readLines(file).isEmpty()) {
            writeText(file, ORDERS_HEADER + "\n");
        }
        String line = String.join(",",
                order.getOrderId(),
                order.getCustomerId(),
                order.getOrderType().name(),
                order.getPickupTime().replace(",", " "),
                String.format("%.2f", subtotal),
                String.format("%.2f", tax),
                String.format("%.2f", total),
                String.valueOf(order.getNutrition().getCalories()),
                itemsToField(order.getItems()));
        Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static String itemsToField(List<OrderItem> items) {
        List<String> parts = new ArrayList<>();
        for (OrderItem item : items) {
            StringBuilder sb = new StringBuilder(item.getItem().getId()).append("x").append(item.getQuantity());
            if (!item.getChosen().isEmpty()) {
                List<String> names = new ArrayList<>();
                for (Modifier m : item.getChosen()) {
                    names.add(m.getName());
                }
                sb.append("(").append(String.join("/", names)).append(")");
            }
            parts.add(sb.toString());
        }
        return String.join(";", parts);
    }
}
