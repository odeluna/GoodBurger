package rewards;

import java.util.List;
import java.util.Objects;

/** In-memory catalog used by order calculations. */
public final class Menu {
    private final List<MenuItem> items;
    public Menu(List<MenuItem> items) { this.items = List.copyOf(Objects.requireNonNull(items)); }
    public List<MenuItem> items() { return items; }
    public MenuItem findById(String id) {
        return items.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown menu item: " + id));
    }
}
