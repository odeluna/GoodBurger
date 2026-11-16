package com.goodburger.data;

import com.goodburger.model.Category;
import com.goodburger.model.Menu;
import com.goodburger.model.MenuItem;
import com.goodburger.model.Modifier;
import com.goodburger.model.Nutrition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns menu.csv and modifiers.csv into a {@link Menu}. This is the <b>only</b> class that reads
 * those files.
 *
 * <p><b>Why keep file reading in one class?</b> If the file format changes (a new column, a
 * different separator), only this class changes. Screens and model classes never know a file
 * exists, which also means they can be tested with menus built in code.</p>
 *
 * <p><b>File formats</b> (first line of each file is a header and is skipped; blank lines are ignored):</p>
 * <pre>
 * menu.csv:      id,name,category,price,calories,protein,carbs,fat,sodium,cateringOnly
 * modifiers.csv: itemId,name,extraPrice,extraCalories
 * </pre>
 * <p>Fields are split on plain commas, so names must not contain commas. That keeps parsing to one
 * {@code split(",")} call instead of needing a CSV library.</p>
 *
 * <p><b>Why two files?</b> One item can have any number of add-ons. Keeping add-ons in their own file,
 * one per line and linked by item id, keeps both files flat and easy to edit in Excel or Sheets.</p>
 */
public class MenuFileReader {

    private static final int MENU_COLUMNS = 10;
    private static final int MODIFIER_COLUMNS = 4;

    /**
     * Reads both files and returns a ready-to-use menu with every add-on attached to its item.
     *
     * <p>Order matters: items are read first so that each add-on line can find its item by id.</p>
     *
     * @param menuPath      path to menu.csv
     * @param modifiersPath path to modifiers.csv
     * @return the loaded menu
     * @throws IOException if a file can't be read, or a line is malformed. The message names the
     *         file and line number so a typo in the CSV is easy to find.
     */
    public Menu load(Path menuPath, Path modifiersPath) throws IOException {
        List<MenuItem> items = new ArrayList<>();
        List<String> menuLines = Files.readAllLines(menuPath);
        for (int i = 1; i < menuLines.size(); i++) {           // i = 1 skips the header row
            String line = menuLines.get(i);
            if (line.isBlank()) continue;
            try {
                items.add(parseItem(line));
            } catch (RuntimeException e) {
                throw new IOException(menuPath.getFileName() + " line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        Menu menu = new Menu(items);

        List<String> modifierLines = Files.readAllLines(modifiersPath);
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

    /**
     * Builds one {@link MenuItem} from one line of menu.csv.
     *
     * @param line e.g. {@code B01,Classic Burger,ENTREE,6.99,650,32,45,35,980,false}
     * @return the item, with no add-ons yet
     * @throws IllegalArgumentException if the column count, a number, or the category is wrong
     */
    MenuItem parseItem(String line) {
        String[] f = split(line, MENU_COLUMNS);
        Nutrition nutrition = new Nutrition(
                Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                Integer.parseInt(f[7]), Integer.parseInt(f[8]));
        return new MenuItem(
                f[0], f[1],
                Category.valueOf(f[2].toUpperCase()),   // "ENTREE" -> Category.ENTREE; unknown text throws
                Double.parseDouble(f[3]),
                nutrition,
                Boolean.parseBoolean(f[9]));
    }

    /**
     * Builds one {@link Modifier} from one line of modifiers.csv. The first column (item id) is used
     * by {@link #load} to find the owner, so it is not part of the Modifier itself.
     *
     * @param line e.g. {@code B01,Add bacon,1.50,120}
     * @return the add-on
     * @throws IllegalArgumentException if the column count or a number is wrong
     */
    Modifier parseModifier(String line) {
        String[] f = split(line, MODIFIER_COLUMNS);
        return new Modifier(f[1], Double.parseDouble(f[2]), Integer.parseInt(f[3]));
    }

    /** Splits a line on commas, trims spaces, and checks the column count so mistakes fail loudly. */
    private static String[] split(String line, int expectedColumns) {
        String[] fields = line.split(",", -1);   // -1 keeps empty trailing fields so the count is honest
        if (fields.length != expectedColumns) {
            throw new IllegalArgumentException("expected " + expectedColumns + " columns but found "
                    + fields.length + " (names can't contain commas)");
        }
        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i].trim();
        }
        return fields;
    }
}
