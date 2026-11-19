package rewards;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Sole reader and writer for the six GoodBurger CSV files. */
public final class DataManager {
    private static final String MENU = "menu.csv", MODIFIERS = "modifiers.csv";
    private static final String ORDERS = "orders.csv", ORDER_ITEMS = "orders_items.csv";
    private static final String USERS = "users.csv", FAVORITES = "favoriteorders.csv";
    private static final String[] FILES = {MENU, MODIFIERS, ORDERS, ORDER_ITEMS, USERS, FAVORITES};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Path baseDirectory;

    public DataManager(Path baseDirectory) { this.baseDirectory = Objects.requireNonNull(baseDirectory); }

    /** Copy bundled seeds once into writable app data, leaving project resources untouched. */
    public static Path resourceDir() throws IOException {
        Path installed = Path.of(System.getProperty("user.home"), ".local", "share",
                "rewards-javafx", "goodburger");
        Files.createDirectories(installed);
        for (String file : FILES) {
            Path target = installed.resolve(file);
            if (Files.exists(target)) continue;
            try (InputStream input = DataManager.class.getResourceAsStream("/goodburger/" + file)) {
                if (input == null) throw new IOException("Missing bundled data: " + file);
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return installed;
    }

    private List<String[]> readRows(String file, String header, int columns) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(baseDirectory.resolve(file), StandardCharsets.UTF_8)) {
            String first = reader.readLine();
            if (!header.equals(first)) throw new IOException("Unexpected header in " + file + ": " + first);
            String line;
            int number = 1;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length != columns)
                    throw new IOException("Expected " + columns + " fields in " + file + " line " + number);
                rows.add(parts);
            }
        }
        return rows;
    }

    private static String field(String value) {
        if (value == null || value.contains(",") || value.contains("\n") || value.contains("\r"))
            throw new IllegalArgumentException("Invalid CSV field: " + value);
        return value;
    }

    public List<Modifier> loadModifiers() throws IOException {
        List<Modifier> result = new ArrayList<>();
        for (String[] row : readRows(MODIFIERS, "itemId,name,extraPrice,extraCalories", 4))
            result.add(new Modifier(row[0], row[1], Double.parseDouble(row[2]),
                    Integer.parseInt(row[3])));
        return result;
    }

    public List<MenuItem> loadMenu() throws IOException {
        List<MenuItem> items = new ArrayList<>();
        Map<String, MenuItem> byId = new HashMap<>();
        for (String[] row : readRows(MENU,
                "id,name,category,price,calories,protein,carbs,fat,sodium,cateringOnly", 10)) {
            MenuItem item = new MenuItem(row[0], row[1], Category.valueOf(row[2]),
                    Double.parseDouble(row[3]), Integer.parseInt(row[4]),
                    Integer.parseInt(row[5]), Integer.parseInt(row[6]),
                    Integer.parseInt(row[7]), Integer.parseInt(row[8]),
                    Boolean.parseBoolean(row[9]));
            if (byId.putIfAbsent(item.id(), item) != null)
                throw new IOException("Duplicate menu item: " + item.id());
            items.add(item);
        }
        for (Modifier modifier : loadModifiers()) {
            MenuItem item = byId.get(modifier.itemId());
            if (item == null) throw new IOException("Unknown modifier item: " + modifier.itemId());
            item.addModifier(modifier);
        }
        return items;
    }

    public List<Order> loadOrders() throws IOException {
        Menu menu = new Menu(loadMenu());
        Map<String, Order> byId = new LinkedHashMap<>();
        Map<String, double[]> stored = new HashMap<>();
        for (String[] row : readRows(ORDERS,
                "orderId,customerId,orderType,total,tax,pickupTime", 6)) {
            Order order = new Order(row[0], row[1], OrderType.valueOf(row[2]), row[5], menu);
            if (byId.putIfAbsent(row[0], order) != null)
                throw new IOException("Duplicate order: " + row[0]);
            stored.put(row[0], new double[] {Double.parseDouble(row[3]), Double.parseDouble(row[4])});
        }
        for (String[] row : readRows(ORDER_ITEMS, "orderId,itemId,quantity,modifierNames", 4)) {
            Order order = byId.get(row[0]);
            if (order == null) throw new IOException("Unknown order for item: " + row[0]);
            MenuItem item;
            try { item = menu.findById(row[1]); }
            catch (IllegalArgumentException error) { throw new IOException(error); }
            List<Modifier> chosen = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            if (!row[3].isEmpty()) for (String name : row[3].split(";", -1)) {
                if (!seen.add(name)) throw new IOException("Duplicate modifier: " + name);
                Modifier match = item.modifiers().stream().filter(m -> m.name().equals(name))
                        .findFirst().orElseThrow(() -> new IOException(
                                "Unknown modifier " + name + " for " + row[1]));
                chosen.add(match);
            }
            order.addItem(new OrderItem(row[1], Integer.parseInt(row[2]), chosen));
        }
        for (Order order : byId.values()) {
            double[] amounts = stored.get(order.orderId());
            if (order.items().isEmpty() || Math.abs(order.total() - amounts[0]) > 0.005
                    || Math.abs(order.tax() - amounts[1]) > 0.005)
                throw new IOException("Order amounts do not match lines: " + order.orderId());
        }
        return new ArrayList<>(byId.values());
    }

    public List<User> loadUsers() throws IOException {
        List<User> users = new ArrayList<>();
        for (String[] row : readRows(USERS, "id,name,birthday,email,password,points", 6))
            users.add(new User(row[0], row[1], row[2], row[3], row[4], Integer.parseInt(row[5])));
        return users;
    }

    public List<SavedOrder> loadFavoriteOrders() throws IOException {
        Map<String, Order> orders = new HashMap<>();
        for (Order order : loadOrders()) orders.put(order.orderId(), order);
        List<SavedOrder> favorites = new ArrayList<>();
        for (String[] row : readRows(FAVORITES, "userId,orderId,addedAt", 3)) {
            Order order = orders.get(row[1]);
            if (order == null) throw new IOException("Unknown favorite order: " + row[1]);
            favorites.add(new SavedOrder(row[0], order, row[2]));
        }
        return favorites;
    }

    private void append(String file, String line) throws IOException {
        Files.writeString(baseDirectory.resolve(file), line + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
    }

    public void saveOrder(Order order) throws IOException {
        Objects.requireNonNull(order);
        if (order.items().isEmpty()) throw new IllegalArgumentException("Cannot save an empty order");
        for (Order existing : loadOrders())
            if (existing.orderId().equals(order.orderId()))
                throw new IllegalArgumentException("Order id already exists: " + order.orderId());
        String header = String.join(",", field(order.orderId()), field(order.customerId()),
                order.orderType().name(), String.format(java.util.Locale.ROOT, "%.2f", order.total()),
                String.format(java.util.Locale.ROOT, "%.2f", order.tax()), field(order.pickupTime()));
        StringBuilder lines = new StringBuilder();
        for (OrderItem item : order.items()) {
            order.menu().findById(item.itemId());
            List<String> names = new ArrayList<>();
            for (Modifier modifier : item.modifiers()) {
                if (order.menu().findById(item.itemId()).modifiers().stream()
                        .noneMatch(allowed -> allowed.equals(modifier)))
                    throw new IllegalArgumentException("Invalid modifier for " + item.itemId());
                names.add(field(modifier.name()));
            }
            lines.append(field(order.orderId())).append(',').append(field(item.itemId()))
                    .append(',').append(item.quantity()).append(',')
                    .append(String.join(";", names)).append('\n');
        }
        append(ORDERS, header);
        Files.writeString(baseDirectory.resolve(ORDER_ITEMS), lines, StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
    }

    public void saveUser(User user) throws IOException {
        Objects.requireNonNull(user);
        if (!user.passwordHash().matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Password must be a lowercase SHA-256 hex digest");
        List<String[]> rows = readRows(USERS, "id,name,birthday,email,password,points", 6);
        String[] updated = {field(user.id()), field(user.name()), field(user.birthday()),
                field(user.email()), user.passwordHash(), Integer.toString(user.points())};
        boolean replaced = false;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i)[0].equals(user.id())) {
            rows.set(i, updated);
            replaced = true;
            break;
        }
        if (!replaced) rows.add(updated);
        StringBuilder output = new StringBuilder("id,name,birthday,email,password,points\n");
        for (String[] row : rows) output.append(String.join(",", row)).append('\n');
        Files.writeString(baseDirectory.resolve(USERS), output, StandardCharsets.UTF_8);
    }

    public void saveFavoriteOrder(String userId, Order order) throws IOException {
        Objects.requireNonNull(order);
        append(FAVORITES, String.join(",", field(userId), field(order.orderId()),
                LocalDateTime.now().format(TIME)));
    }
}
