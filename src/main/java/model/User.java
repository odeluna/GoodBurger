package model;

public class User {

    private String id;
    private String name;
    private String birthday;
    private String email;
    private String password;
    private boolean isGuest;
    private int points;

    // Constructs a User with personal info, login credentials, guest status and points
    public User(String name, String birthday, String email, String password, boolean isGuest, int points) {
        this("", name, birthday, email, password, isGuest, points);
    }

    public User(String id, String name, String birthday, String email, String password, boolean isGuest, int points) {
        this.id = id;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
        this.password = password;
        this.isGuest = isGuest;
        setPoints(points);
    }
    public User() {
        this("", "", "", "", "", true, 0);
    }

    // setters and getters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // Returns true if this user is browsing as a guest
    public boolean isGuest() {
        return isGuest;
    }

    public void setGuest(boolean guest) {
        isGuest = guest;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        if (points >= 0) {
            this.points = points;
        }
    }

    // Returns a summary of the User details without the password
    @Override
    public String toString() {
        return "Name= " + name + ", Birthday= " + birthday + ", Email= " + email + ", Points= " + points;
    }
}