package model;

import java.util.ArrayList;

public class Item {
    private String name;
    private double price;
    private int calories;
    private String picture_path;

    public Item(String name, double price, int calories) {
        this.name = name;
        this.price = price;
        this.calories = calories;
    }

    public Item(String name, double price, int calories, String picture_path) {
        this.name = name;
        this.price = price;
        this.calories = calories;
        this.picture_path = picture_path;
    }


}
