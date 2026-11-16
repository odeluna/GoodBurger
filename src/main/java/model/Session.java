package model;

import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class Session {
    private User currentUser;
    private Order currentOrder;
    private String FILE_PATH = "favoriteOrders.txt";
    ArrayList<Order> favoriteOrders = new ArrayList<>();

    public Session() {

    }

    public void loadFavoriteOrders() {
        File file = new File(FILE_PATH);
        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

            }
        } catch (Exception e) {
            System.out.println("Error loading favorite orders:");
        }
    }

    public void submitOrder() {

    }

    private void lineToOrder(String line) {
        String[] parts = line.split(",");


    }

    private void orderToLine(Order order) {

    }



}
