package servlet;

import jakarta.servlet.http.HttpSession;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class MySession {
    private HttpSession httpSession;

    public MySession() {
    }

    public MySession(HttpSession httpSession) {
        this.httpSession = httpSession;
    }

    public HttpSession getHttpSession() {
        return httpSession;
    }

    public void setHttpSession(HttpSession httpSession) {
        this.httpSession = httpSession;
    }

    public Object get(String key) {
        return (httpSession != null && key != null) ? httpSession.getAttribute(key) : null;
    }

    public void add(String key, Object value) {
        if (httpSession != null && key != null) {
            httpSession.setAttribute(key, value);
        }
    }

    public void put(String key, Object value) {
        add(key, value);
    }

    public void remove(String key) {
        if (httpSession != null && key != null) {
            httpSession.removeAttribute(key);
        }
    }

    public void delete(String key) {
        remove(key);
    }

    public boolean containsKey(String key) {
        return get(key) != null;
    }

    public void invalidate() {
        if (httpSession != null) {
            httpSession.invalidate();
        }
    }

    public Map<String, Object> getAll() {
        Map<String, Object> map = new HashMap<>();
        if (httpSession != null) {
            Enumeration<String> names = httpSession.getAttributeNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                map.put(name, httpSession.getAttribute(name));
            }
        }
        return map;
    }
}
