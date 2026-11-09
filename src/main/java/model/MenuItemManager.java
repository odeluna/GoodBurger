package model;

import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;


public class MenuItemManager {
    private ArrayList<MenuItem> items;

    public MenuItemManager() {
        items = new ArrayList<>();
    }
    public ArrayList<MenuItem> getItems() {
        return items;
    }


    public void loadItems() {
        File file = new File("data/menu_items.txt");


    }
}
