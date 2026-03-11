package servlet.security;

public interface UserSession {
    String[] getRoles();
    boolean hasRole(String role);
}
