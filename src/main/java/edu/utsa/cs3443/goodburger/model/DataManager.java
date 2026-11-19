package edu.utsa.cs3443.goodburger.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The only class that reads  and writes the files in the data folder:
 * <ul><li>menu.csv</li><li>modifiers.csv</li><li>orders.csv</li><li>users.csv</li> </ul>
 * <p>Other classes ask DataManager for data instead of opening files themselves, so a change
 * to a file's location or format only has to be made here.</p>
 */

public class DataManager {
    //TODO: Update order to reflect nutrition format
    public static final String ORDERS_HEADER =
            "orderId,customerId,orderType,pickupTime,subtotal,tax,total,calories,items";

    private static final int MENU_COLUMNS = 10;
    private static final int MODIFIER_COLUMNS = 8;

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

    // --------------------- FILE HELPERS -------------------------- //

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

    // ----------------------------- MENU ---------------------------------- //


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
    MenuItem parseItem(String line) {
        String[] f = split(line, MENU_COLUMNS);
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

    // modifiers.csv: itemId,name,extraPrice,extraCalories, extraProtein, extraCarbs, extraFat, extraSodium
    Modifier parseModifier(String line) {
        String[] f = split(line, MODIFIER_COLUMNS);
        int calories, protein, carbs, fat, sodium;
        try {
            calories = Integer.parseInt(f[3]);
            protein = Integer.parseInt(f[4]);
            carbs = Integer.parseInt(f[5]);
            fat = Integer.parseInt(f[6]);
            sodium = Integer.parseInt(f[7]);
        } catch (NumberFormatException e) {
            calories = 0;
            protein = 0;
            carbs = 0;
            fat = 0;
            sodium = 0;
        }
        return new Modifier(f[1], Double.parseDouble(f[2]), calories, protein, carbs, fat, sodium);
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

    // ----------------------------- order --------------------------------- //

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
