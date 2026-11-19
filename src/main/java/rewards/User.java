package rewards;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Account seed data and password verification. */
public final class User {
    private final String id, name, birthday, email, passwordHash;
    private final int points;
    public User(String id, String name, String birthday, String email, String passwordHash, int points) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.birthday = Objects.requireNonNull(birthday);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.points = points;
    }
    public String id() { return id; }
    public String name() { return name; }
    public String birthday() { return birthday; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public int points() { return points; }
    public static String hashPassword(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Objects.requireNonNull(password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
    public boolean checkPassword(String plaintext) {
        return hashPassword(plaintext).equals(passwordHash);
    }
}
