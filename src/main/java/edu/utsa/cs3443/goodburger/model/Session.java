package edu.utsa.cs3443.goodburger.model;

import java.io.IOException;

/**
 * Everything the screens share while the app is open: the signed-in user and the managers.
 *
 * <p>JavaFX creates a new controller every time a screen opens, so controllers can't keep this
 * state themselves. Instead, each one calls {@code Session.get()} and gets the same object.</p>
 */
public final class Session {

    private static Session instance;

    private final DataManager dataManager;
    private final AccountManager accountManager;
    private final OrderManager orderManager;
    private MenuManager menuManager;
    private User currentUser;

    private Session(DataManager dataManager) {
        this.dataManager = dataManager;
        this.accountManager = new AccountManager(dataManager);
        this.orderManager = new OrderManager(dataManager);
    }

    public static Session get() {
        if (instance == null) {
            instance = new Session(new DataManager());
        }
        return instance;
    }

    /** Reads the menu files once at startup. */
    public void loadMenu() throws IOException {
        menuManager = dataManager.loadMenu();
    }

    public DataManager getDataManager()       { return dataManager; }
    public AccountManager getAccountManager() { return accountManager; }
    public OrderManager getOrderManager()     { return orderManager; }
    public MenuManager getMenuManager()       { return menuManager; }

    public User getCurrentUser()              { return currentUser; }
    public void setCurrentUser(User user)     { this.currentUser = user; }
}
