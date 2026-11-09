package model;

import java.util.ArrayList;

public class MenuItem extends Item {
    private ArrayList<Item> ingredients;
    private int quantity;
    private double menuPrice;

    public MenuItem(String name, double price, int calories, String picture_path, ArrayList<Item> ingredients, int quantity) {
        super(name, price, calories, picture_path);
        this.ingredients = ingredients;
        this.quantity = 1;
    }
}
