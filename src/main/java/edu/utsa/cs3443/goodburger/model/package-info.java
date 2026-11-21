/**
 * Domain and data layer.
 *
 * <ul>
 *   <li>Session - the single sign-in session (current user + loaded menu);
 *       the only source of truth for "who is signed in".</li>
 *   <li>AccountManager - user accounts in data/users.csv (register / sign in / delete).</li>
 *   <li>MenuManager, MenuItem, Category, Modifier, Nutrition - the menu loaded from data/menu.csv.</li>
 *   <li>Order, OrderItem, OrderType, OrderManager - orders, persisted to data/orders.csv.</li>
 *   <li>DataManager - owns the data/ folder: quote-aware CSV parsing and file IO.</li>
 * </ul>
 */
package edu.utsa.cs3443.goodburger.model;
