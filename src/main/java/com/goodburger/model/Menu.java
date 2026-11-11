package com.goodburger.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every {@link MenuItem} the restaurant sells, with the lookups the screens need.
 *
 * <p><b>Why a class instead of passing a List around?</b> Screens never need "all items"; they
 * need "the entrees", "item B01" or "the catering items". Putting those questions here means the
 * filtering rules live in one place, and the UI never loops over the raw list itself.</p>
 *
 * <p><b>How it is created and shared:</b> {@link com.goodburger.data.MenuFileReader#load} builds
 * one Menu from the CSV files when the app starts, and that single object is passed to
 * {@link com.goodburger.ui.menu.MenuScreen}. Nothing else reads the files.</p>
 */
public class Menu {

    private final List<MenuItem> items;

    /** @param items every item read from menu.csv, in file order (so the file decides button order) */
    public Menu(List<MenuItem> items) {
        this.items = new ArrayList<>(items);
    }

    /**
     * Items for one category button on the <b>regular</b> menu.
     *
     * <p>Catering-only items are left out on purpose: a party tray shouldn't appear next to a single
     * burger. They are only reachable through {@link #getCateringItems()}.</p>
     *
     * @param c the category the customer clicked
     * @return matching items in file order; an empty list (never null) if there are none
     */
    public List<MenuItem> getByCategory(Category c) {
        List<MenuItem> result = new ArrayList<>();
        for (MenuItem item : items) {
            if (item.getCategory() == c && !item.isCateringOnly()) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Finds an item by its id. Used when a file line refers to an item, e.g. modifiers.csv,
     * or the rewards and quick-order files your teammates write.
     *
     * @param id the item's id, e.g. "B01"
     * @return the item, or {@code null} if no item has that id
     */
    public MenuItem findById(String id) {
        for (MenuItem item : items) {
            if (item.getId().equals(id)) {
                return item;
            }
        }
        return null;
    }

    /**
     * The catering menu. The proposal describes it as "a modified version of the menu", so it is
     * the same data filtered by a flag rather than a second class or file.
     *
     * @return every item marked cateringOnly, in file order
     */
    public List<MenuItem> getCateringItems() {
        List<MenuItem> result = new ArrayList<>();
        for (MenuItem item : items) {
            if (item.isCateringOnly()) {
                result.add(item);
            }
        }
        return result;
    }

    /** @return every item, read-only. Mainly useful for testing. */
    public List<MenuItem> getAllItems() {
        return Collections.unmodifiableList(items);
    }
}
