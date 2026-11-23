
package edu.utsa.cs3443.goodburger.model;

/**
 * AccountManager handles reading, saving, searching and deleting user accounts.
 * It accesses users.csv to store its data, and keeps track of who is signed in.
 *
 * author: Anthony Lassiter
 * class: CS3443
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AccountManager {

    // Column names for storing user information
    private static final String CSV_HEADER = "id,name,birthday,email,password,points";

    private final DataManager dataManager;
    private File file;

    // Creates an AccountManager using the default users.csv file
    public AccountManager() {
        this(new DataManager());
    }

    // Uses the users.csv file that the DataManager points at
    public AccountManager(DataManager dataManager) {
        this.dataManager = dataManager;
        this.file = dataManager.getUsersFile().toFile();
    }

    // Returns the user who is currently signed in
    public User getCurrentUser() {
        return Session.get().getCurrentUser();
    }

    // Checks the user's email and password to log them in
    public boolean login(String email, String password) {
        User user = findUser(email);

        if (user != null && password != null && password.equals(user.getPassword())) {
            Session.get().setCurrentUser(user);
            return true;
        }

        return false;
    }

    // Creates a new account and saves it to users.csv
    public boolean registerUser(String name, String birthday, String email, String password) {

        // Makes sure all registration information is valid
        if (!isValidField(name) || !isValidField(birthday)
                || !isValidField(email) || !isValidField(password)
                || name.trim().isEmpty() || birthday.trim().isEmpty()
                || email.trim().isEmpty() || password.isEmpty()) {
            return false;
        }

        // Loads the existing accounts from the file
        List<User> users = readUsers();

        if (users == null) {
            return false;
        }

        // Checks if an account already uses this email
        for (User user : users) {
            if (user.getEmail().equalsIgnoreCase(email.trim())) {
                return false;
            }
        }

        // Creates a new ID and adds the new user to the list
        String id = createNextId(users);

        User newUser = new User(id, name.trim(), birthday.trim(),
                email.trim(), password, false, 0);

        users.add(newUser);

        // Saves the new account and signs the user in
        if (saveUserToFile(users)) {
            Session.get().setCurrentUser(newUser);
            return true;
        }

        return false;
    }

    // Creates a temporary guest account without saving it to the file
    public void continueAsGuest() {
        Session.get().setCurrentUser(
                new User("Guest", "", "", "", true, 0));
    }

    // Signs out the current user
    public void logout() {
        Session.get().setCurrentUser(null);
    }

    // Removes an account from users.csv
    public boolean deleteAccount(User user) {

        // Guests cannot delete an account
        if (user == null || user.isGuest()) {
            return false;
        }

        // Loads all existing users
        List<User> users = readUsers();

        if (users == null) {
            return false;
        }

        // Searches for the account and removes it
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getEmail().equalsIgnoreCase(user.getEmail())) {
                users.remove(i);

                // Saves the updated list and signs the user out
                if (saveUserToFile(users)) {
                    Session.get().setCurrentUser(null);
                    return true;
                }

                return false;
            }
        }

        return false;
    }

    // Searches for an account using its email address
    public User findUser(String email) {

        if (email == null) {
            return null;
        }

        // Checks each user until the matching email is found
        for (User user : loadUserFromFile()) {
            if (user.getEmail().equalsIgnoreCase(email.trim())) {
                return user;
            }
        }

        return null;
    }

    // Loads all users from the CSV file into an ArrayList
    public List<User> loadUserFromFile() {
        List<User> users = readUsers();

        if (users == null) {
            return new ArrayList<>();
        }

        return users;
    }

    // Saves all users in the list back into users.csv
    public boolean saveUserToFile(List<User> users) {

        if (users == null) {
            return false;
        }

        // Starts the file with the CSV column headers
        String contents = CSV_HEADER + "\n";

        // Converts each user into a CSV line
        for (User user : users) {
            if (!isValidUser(user)) {
                return false;
            }

            contents += userToFileLine(user) + "\n";
        }

        try {
            // DataManager creates the data folder if needed and writes the file
            dataManager.writeText(file.toPath(), contents);

            return true;

        } catch (IOException e) {
            System.out.println("Could not save users");
            return false;
        }
    }

    // Reads users.csv and converts each line into a User object
    private List<User> readUsers() {

        List<User> users = new ArrayList<>();

        try {
            Path path = file.toPath();

            // Returns an empty list if the file does not exist yet
            if (!dataManager.exists(path)) {
                return users;
            }

            List<String> lines = dataManager.readLines(path);

            // Makes sure the file has the correct column headers
            if (lines.isEmpty() || !lines.get(0).equals(CSV_HEADER)) {
                return null;
            }

            // Skips the header and reads each user record
            for (int i = 1; i < lines.size(); i++) {
                if (!lines.get(i).trim().isEmpty()) {
                    User user = parseUserFromFile(lines.get(i));

                    if (user == null) {
                        return null;
                    }

                    users.add(user);
                }
            }

            return users;

        } catch (IOException e) {
            return null;
        }
    }

    // Finds the highest user ID and creates the next available ID
    private String createNextId(List<User> users) {

        int maxId = 0;

        for (User user : users) {
            try {
                int id = Integer.parseInt(user.getId());

                if (id > maxId) {
                    maxId = id;
                }

            } catch (NumberFormatException e) {
                return null;
            }
        }

        // Keeps IDs at least three digits long (001, 002, 003)
        return String.format("%03d", maxId + 1);
    }

    // Checks that a user has all the required information
    private boolean isValidUser(User user) {

        if (user == null || !isValidField(user.getId())
                || !isValidField(user.getName())
                || !isValidField(user.getBirthday())
                || !isValidField(user.getEmail())
                || !isValidField(user.getPassword())
                || user.getName().trim().isEmpty()
                || user.getBirthday().trim().isEmpty()
                || user.getEmail().trim().isEmpty()
                || user.getPassword().isEmpty()
                || user.getPoints() < 0) {
            return false;
        }

        try {
            return Integer.parseInt(user.getId()) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Makes sure a field does not contain characters that would break the CSV format
    private boolean isValidField(String value) {
        return value != null && !value.contains(",")
                && !value.contains("\n") && !value.contains("\r");
    }

    // Converts one line from users.csv into a User object
    public User parseUserFromFile(String line) {

        if (line == null) {
            return null;
        }

        // Separates the information using commas
        String[] parts = new String[6];
        int start = 0;

        for (int i = 0; i < 5; i++) {
            int comma = line.indexOf(",", start);

            if (comma == -1) {
                return null;
            }

            parts[i] = line.substring(start, comma);
            start = comma + 1;
        }

        parts[5] = line.substring(start);

        try {
            // Gets each piece of user information from the CSV line
            String id = parts[0].trim();
            String name = parts[1].trim();
            String birthday = parts[2].trim();
            String email = parts[3].trim();
            String password = parts[4];
            int points = Integer.parseInt(parts[5].trim());

            // Checks that the required information is present
            if (name.isEmpty() || birthday.isEmpty()
                    || email.isEmpty() || password.isEmpty()
                    || points < 0 || Integer.parseInt(id) < 0) {
                return null;
            }

            // Creates and returns the User object
            return new User(id, name, birthday, email, password, false, points);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Converts a User object into a comma-separated line for users.csv
    public String userToFileLine(User user) {

        if (user == null) {
            return "";
        }

        return user.getId() + ","
                + user.getName() + ","
                + user.getBirthday() + ","
                + user.getEmail() + ","
                + user.getPassword() + ","
                + user.getPoints();
    }
}
