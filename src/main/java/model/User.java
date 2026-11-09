package model;

public class User {
    private String name;
    private String birthday;
    private String email;
    private String password;
    private int points;
    private boolean isGuest;
    private Order favoriteOrder;

    public User(String name, String birthday, String email, String password,
                int points, Order favoriteOrder) {
        this.name = name;
        this.birthday = birthday;
        this.email = email;
        this.password = password;
        this.points = points;
        this.isGuest = false;
        this.favoriteOrder = favoriteOrder;

    }

    public User() {
        this.name = "";
        this.birthday = "";
        this.email = "";
        this.password = "";
        this.points = 0;
        this.isGuest = true;
        this.favoriteOrder = null;
    }
}
