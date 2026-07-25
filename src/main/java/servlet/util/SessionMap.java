package servlet.util;

import jakarta.servlet.http.HttpSession;

import java.util.*;

public class SessionMap implements Map<String, Object> {
    private final HttpSession httpSession;

    public SessionMap(HttpSession httpSession) {
        this.httpSession = httpSession;
    }

    public HttpSession getHttpSession() {
        return httpSession;
    }

    @Override
    public int size() {
        if (httpSession == null) return 0;
        int count = 0;
        Enumeration<String> names = httpSession.getAttributeNames();
        while (names.hasMoreElements()) {
            names.nextElement();
            count++;
        }
        return count;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        if (httpSession == null || key == null) return false;
        return httpSession.getAttribute(key.toString()) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        if (httpSession == null) return false;
        Enumeration<String> names = httpSession.getAttributeNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            Object val = httpSession.getAttribute(name);
            if (Objects.equals(val, value)) return true;
        }
        return false;
    }

    @Override
    public Object get(Object key) {
        if (httpSession == null || key == null) return null;
        return httpSession.getAttribute(key.toString());
    }

    @Override
    public Object put(String key, Object value) {
        if (httpSession == null || key == null) return null;
        Object old = httpSession.getAttribute(key);
        httpSession.setAttribute(key, value);
        return old;
    }

    @Override
    public Object remove(Object key) {
        if (httpSession == null || key == null) return null;
        Object old = httpSession.getAttribute(key.toString());
        httpSession.removeAttribute(key.toString());
        return old;
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        if (httpSession == null || m == null) return;
        for (Entry<? extends String, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        if (httpSession == null) return;
        List<String> keys = new ArrayList<>();
        Enumeration<String> names = httpSession.getAttributeNames();
        while (names.hasMoreElements()) {
            keys.add(names.nextElement());
        }
        for (String k : keys) {
            httpSession.removeAttribute(k);
        }
    }

    @Override
    public Set<String> keySet() {
        Set<String> set = new HashSet<>();
        if (httpSession != null) {
            Enumeration<String> names = httpSession.getAttributeNames();
            while (names.hasMoreElements()) {
                set.add(names.nextElement());
            }
        }
        return set;
    }

    @Override
    public Collection<Object> values() {
        List<Object> list = new ArrayList<>();
        if (httpSession != null) {
            Enumeration<String> names = httpSession.getAttributeNames();
            while (names.hasMoreElements()) {
                list.add(httpSession.getAttribute(names.nextElement()));
            }
        }
        return list;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> set = new HashSet<>();
        if (httpSession != null) {
            Enumeration<String> names = httpSession.getAttributeNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                set.add(new AbstractMap.SimpleEntry<>(name, httpSession.getAttribute(name)));
            }
        }
        return set;
    }
}
