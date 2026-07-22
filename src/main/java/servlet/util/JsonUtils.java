package servlet.util;

import servlet.ModelView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonUtils {

    // Format final de la reponse API REST pour le Sprint 9
    public static String formatApiResponse(Object result, int statusCode, String statusMsg) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", statusMsg != null ? statusMsg : "success");
        response.put("code", statusCode);

        Object dataContent = result;
        if (result instanceof ModelView) {
            dataContent = ((ModelView) result).getData();
        }

        if (dataContent != null && (dataContent.getClass().isArray() || dataContent instanceof Collection)) {
            int count = 0;
            if (dataContent instanceof Collection) {
                count = ((Collection<?>) dataContent).size();
            } else if (dataContent.getClass().isArray()) {
                count = java.lang.reflect.Array.getLength(dataContent);
            }
            response.put("count", count);
            response.put("data", dataContent);
        } else {
            response.put("data", dataContent);
        }

        return toJson(response);
    }

    public static String formatApiError(int statusCode, String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("code", statusCode);
        response.put("message", errorMessage != null ? errorMessage : "Erreur interne");
        return toJson(response);
    }

    // Convertisseur generique vers String JSON
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }

        if (obj instanceof String || obj instanceof Character) {
            return "\"" + escapeJson(obj.toString()) + "\"";
        }

        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }

        if (obj instanceof Enum) {
            return "\"" + escapeJson(((Enum<?>) obj).name()) + "\"";
        }

        if (obj instanceof java.util.Date) {
            return "\"" + escapeJson(obj.toString()) + "\"";
        }

        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) continue;
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey().toString())).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        if (obj instanceof Collection) {
            Collection<?> col = (Collection<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }

        if (obj.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("[");
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(java.lang.reflect.Array.get(obj, i)));
            }
            sb.append("]");
            return sb.toString();
        }

        if (obj instanceof ModelView) {
            return toJson(((ModelView) obj).getData());
        }

        // POJO / JavaBean (Reflection)
        return pojoToJson(obj);
    }

    private static String pojoToJson(Object obj) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        Class<?> clazz = obj.getClass();

        // 1. Essayer d'utiliser les getters public
        Method[] methods = clazz.getMethods();
        List<Method> getters = new ArrayList<>();
        for (Method m : methods) {
            if (m.getDeclaringClass() == Object.class) continue;
            if (m.getParameterCount() == 0 && Modifier.isPublic(m.getModifiers())) {
                String name = m.getName();
                if ((name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2)) {
                    getters.add(m);
                }
            }
        }

        if (!getters.isEmpty()) {
            for (Method m : getters) {
                try {
                    String name = m.getName();
                    String propName;
                    if (name.startsWith("get")) {
                        propName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                    } else {
                        propName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                    }
                    Object val = m.invoke(obj);
                    if (!first) sb.append(",");
                    sb.append("\"").append(escapeJson(propName)).append("\":");
                    sb.append(toJson(val));
                    first = false;
                } catch (Exception ignored) {}
            }
        } else {
            // 2. Fallback sur les champs directes
            Field[] fields = clazz.getDeclaredFields();
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) continue;
                f.setAccessible(true);
                try {
                    Object val = f.get(obj);
                    if (!first) sb.append(",");
                    sb.append("\"").append(escapeJson(f.getName())).append("\":");
                    sb.append(toJson(val));
                    first = false;
                } catch (Exception ignored) {}
            }
        }

        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int k = 0; k < 4 - hex.length(); k++) sb.append('0');
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
