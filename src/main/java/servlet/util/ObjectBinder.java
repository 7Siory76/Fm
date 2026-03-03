package servlet.util;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public class ObjectBinder {

    // Instancier et remplir un objet depuis la requete HTTP
    public static Object bindObject(Class<?> clazz, String paramName, HttpServletRequest request) {
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            populateObject(instance, paramName, request);
            return instance;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Binder un tableau d'objets (ex: Employee[] es) depuis es[0].name, es[1].name...
    public static Object bindArray(Class<?> componentType, String paramName, HttpServletRequest request) {
        try {
            int maxIndex = -1;
            Enumeration<String> names = request.getParameterNames();
            String prefixBracket = paramName + "[";
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (name.startsWith(prefixBracket)) {
                    int close = name.indexOf(']');
                    if (close > prefixBracket.length()) {
                        String idxStr = name.substring(prefixBracket.length(), close);
                        try {
                            int idx = Integer.parseInt(idxStr);
                            if (idx > maxIndex) maxIndex = idx;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (maxIndex < 0) {
                // Aucun parametre avec indice es[i] trouve, essayer getParameterValues(paramName)
                String[] values = request.getParameterValues(paramName);
                if (values != null && values.length > 0) {
                    Object arr = java.lang.reflect.Array.newInstance(componentType, values.length);
                    for (int i = 0; i < values.length; i++) {
                        java.lang.reflect.Array.set(arr, i, convertParam(values[i], componentType));
                    }
                    return arr;
                }
                return java.lang.reflect.Array.newInstance(componentType, 0);
            }

            Object arrayInstance = java.lang.reflect.Array.newInstance(componentType, maxIndex + 1);
            for (int i = 0; i <= maxIndex; i++) {
                Object item = componentType.getDeclaredConstructor().newInstance();
                String itemPrefix = paramName + "[" + i + "]";
                populateObject(item, itemPrefix, request);
                java.lang.reflect.Array.set(arrayInstance, i, item);
            }
            return arrayInstance;
        } catch (Exception e) {
            e.printStackTrace();
            return java.lang.reflect.Array.newInstance(componentType, 0);
        }
    }

    public static void populateObject(Object target, String paramName, HttpServletRequest request) {
        if (target == null) return;
        Map<String, String[]> map = request.getParameterMap();

        String prefixDot = (paramName != null && !paramName.isEmpty()) ? paramName + "." : "";
        String prefixBracket = (paramName != null && !paramName.isEmpty()) ? paramName + "[" : "";

        for (Map.Entry<String, String[]> entry : map.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            if (values == null || values.length == 0) continue;

            String propPath = null;
            if (!prefixDot.isEmpty() && key.startsWith(prefixDot)) {
                propPath = key.substring(prefixDot.length());
            } else if (!prefixBracket.isEmpty() && key.startsWith(prefixBracket)) {
                propPath = key.substring(paramName.length());
                if (propPath.startsWith(".")) propPath = propPath.substring(1);
            } else if (paramName == null || paramName.isEmpty() || !hasPrefixedParams(map, prefixDot)) {
                propPath = key;
            }

            if (propPath != null && !propPath.isEmpty()) {
                setPropertyValue(target, propPath, values);
            }
        }
    }

    private static boolean hasPrefixedParams(Map<String, String[]> map, String prefixDot) {
        if (prefixDot.isEmpty()) return false;
        for (String key : map.keySet()) {
            if (key.startsWith(prefixDot)) return true;
        }
        return false;
    }

    public static void setPropertyValue(Object target, String propPath, String[] values) {
        if (target == null || propPath == null || propPath.isEmpty()) return;

        String[] parts = propPath.split("\\.", 2);
        String currentSegment = parts[0];

        String propName = currentSegment;
        Integer index = null;

        if (currentSegment.contains("[") && currentSegment.endsWith("]")) {
            int open = currentSegment.indexOf('[');
            propName = currentSegment.substring(0, open);
            String idxStr = currentSegment.substring(open + 1, currentSegment.length() - 1);
            try {
                index = Integer.parseInt(idxStr);
            } catch (NumberFormatException ignored) {}
        }

        if (parts.length == 1) {
            if (index != null) {
                setIndexedLeafProperty(target, propName, index, values);
            } else {
                setSimpleProperty(target, propName, values);
            }
        } else {
            String remainingPath = parts[1];
            Object childObj = getOrCreateChildObject(target, propName, index);
            if (childObj != null) {
                setPropertyValue(childObj, remainingPath, values);
            }
        }
    }

    private static void setSimpleProperty(Object target, String propName, String[] values) {
        Class<?> clazz = target.getClass();
        Field field = findField(clazz, propName);
        Method setter = findSetter(clazz, propName);

        Class<?> propType = (setter != null) ? setter.getParameterTypes()[0] : (field != null ? field.getType() : null);
        if (propType == null) return;

        Object valueToSet;
        if (propType.isArray()) {
            Class<?> compType = propType.getComponentType();
            Object arr = java.lang.reflect.Array.newInstance(compType, values.length);
            for (int i = 0; i < values.length; i++) {
                java.lang.reflect.Array.set(arr, i, convertParam(values[i], compType));
            }
            valueToSet = arr;
        } else {
            valueToSet = convertParam(values[0], propType);
        }

        try {
            if (setter != null) {
                setter.setAccessible(true);
                setter.invoke(target, valueToSet);
            } else if (field != null) {
                field.setAccessible(true);
                field.set(target, valueToSet);
            }
        } catch (Exception ignored) {}
    }

    private static void setIndexedLeafProperty(Object target, String propName, int index, String[] values) {
        Object child = getOrCreateChildObject(target, propName, null);
        if (child == null) return;

        Class<?> clazz = target.getClass();
        Field field = findField(clazz, propName);
        Method getter = findGetter(clazz, propName);
        Method setter = findSetter(clazz, propName);
        Class<?> propType = (getter != null) ? getter.getReturnType() : (field != null ? field.getType() : null);

        if (propType != null && propType.isArray()) {
            Class<?> compType = propType.getComponentType();
            int currentLen = java.lang.reflect.Array.getLength(child);
            Object arr = child;
            if (currentLen <= index) {
                Object newArr = java.lang.reflect.Array.newInstance(compType, index + 1);
                System.arraycopy(arr, 0, newArr, 0, currentLen);
                arr = newArr;
                try {
                    if (setter != null) {
                        setter.setAccessible(true);
                        setter.invoke(target, arr);
                    } else if (field != null) {
                        field.setAccessible(true);
                        field.set(target, arr);
                    }
                } catch (Exception ignored) {}
            }
            java.lang.reflect.Array.set(arr, index, convertParam(values[0], compType));
        } else if (child instanceof List) {
            List list = (List) child;
            while (list.size() <= index) {
                list.add(null);
            }
            Class<?> compType = getGenericListType(field, getter);
            if (compType == null) compType = String.class;
            list.set(index, convertParam(values[0], compType));
        }
    }

    private static Object getOrCreateChildObject(Object target, String propName, Integer index) {
        Class<?> clazz = target.getClass();
        Field field = findField(clazz, propName);
        Method getter = findGetter(clazz, propName);
        Method setter = findSetter(clazz, propName);

        Class<?> propType = (getter != null) ? getter.getReturnType() : (field != null ? field.getType() : null);
        if (propType == null) return null;

        Object currentVal = null;
        try {
            if (getter != null) {
                getter.setAccessible(true);
                currentVal = getter.invoke(target);
            } else if (field != null) {
                field.setAccessible(true);
                currentVal = field.get(target);
            }
        } catch (Exception ignored) {}

        if (index == null) {
            if (currentVal == null) {
                try {
                    currentVal = propType.getDeclaredConstructor().newInstance();
                    if (setter != null) {
                        setter.setAccessible(true);
                        setter.invoke(target, currentVal);
                    } else if (field != null) {
                        field.setAccessible(true);
                        field.set(target, currentVal);
                    }
                } catch (Exception ignored) {}
            }
            return currentVal;
        } else {
            if (propType.isArray()) {
                Class<?> compType = propType.getComponentType();
                int currentLen = (currentVal != null) ? java.lang.reflect.Array.getLength(currentVal) : 0;
                if (currentVal == null || currentLen <= index) {
                    Object newArr = java.lang.reflect.Array.newInstance(compType, index + 1);
                    if (currentVal != null) {
                        System.arraycopy(currentVal, 0, newArr, 0, currentLen);
                    }
                    currentVal = newArr;
                    try {
                        if (setter != null) {
                            setter.setAccessible(true);
                            setter.invoke(target, currentVal);
                        } else if (field != null) {
                            field.setAccessible(true);
                            field.set(target, currentVal);
                        }
                    } catch (Exception ignored) {}
                }
                Object elem = java.lang.reflect.Array.get(currentVal, index);
                if (elem == null) {
                    try {
                        elem = compType.getDeclaredConstructor().newInstance();
                        java.lang.reflect.Array.set(currentVal, index, elem);
                    } catch (Exception ignored) {}
                }
                return elem;
            } else if (List.class.isAssignableFrom(propType)) {
                if (currentVal == null) {
                    currentVal = new ArrayList<>();
                    try {
                        if (setter != null) {
                            setter.setAccessible(true);
                            setter.invoke(target, currentVal);
                        } else if (field != null) {
                            field.setAccessible(true);
                            field.set(target, currentVal);
                        }
                    } catch (Exception ignored) {}
                }
                List list = (List) currentVal;
                while (list.size() <= index) {
                    list.add(null);
                }
                Object elem = list.get(index);
                if (elem == null) {
                    Class<?> compType = getGenericListType(field, getter);
                    if (compType != null) {
                        try {
                            elem = compType.getDeclaredConstructor().newInstance();
                            list.set(index, elem);
                        } catch (Exception ignored) {}
                    }
                }
                return elem;
            }
        }
        return null;
    }

    private static Class<?> getGenericListType(Field field, Method getter) {
        Type genericType = null;
        if (getter != null) {
            genericType = getter.getGenericReturnType();
        } else if (field != null) {
            genericType = field.getGenericType();
        }
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class) {
                return (Class<?>) args[0];
            }
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getName().equalsIgnoreCase(name)) return f;
        }
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            return findField(clazz.getSuperclass(), name);
        }
        return null;
    }

    private static Method findSetter(Class<?> clazz, String name) {
        String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
        for (Method m : clazz.getDeclaredMethods()) {
            if ((m.getName().equalsIgnoreCase(setterName) || m.getName().equalsIgnoreCase("set" + name))
                    && m.getParameterCount() == 1) {
                return m;
            }
        }
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            return findSetter(clazz.getSuperclass(), name);
        }
        return null;
    }

    private static Method findGetter(Class<?> clazz, String name) {
        String getterName = "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
        String isName = "is" + name.substring(0, 1).toUpperCase() + name.substring(1);
        for (Method m : clazz.getDeclaredMethods()) {
            if ((m.getName().equalsIgnoreCase(getterName) || m.getName().equalsIgnoreCase(isName) || m.getName().equalsIgnoreCase("get" + name))
                    && m.getParameterCount() == 0) {
                return m;
            }
        }
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            return findGetter(clazz.getSuperclass(), name);
        }
        return null;
    }

    public static Object convertParam(String value, Class<?> type) {
        if (value == null) {
            return getDefaultPrimitive(type);
        }
        if (type == String.class) {
            return value;
        } else if (type == int.class || type == Integer.class) {
            if (value.trim().isEmpty()) return type == int.class ? 0 : null;
            return Integer.parseInt(value.trim());
        } else if (type == long.class || type == Long.class) {
            if (value.trim().isEmpty()) return type == long.class ? 0L : null;
            return Long.parseLong(value.trim());
        } else if (type == double.class || type == Double.class) {
            if (value.trim().isEmpty()) return type == double.class ? 0.0 : null;
            return Double.parseDouble(value.trim());
        } else if (type == float.class || type == Float.class) {
            if (value.trim().isEmpty()) return type == float.class ? 0.0f : null;
            return Float.parseFloat(value.trim());
        } else if (type == boolean.class || type == Boolean.class) {
            if (value.trim().isEmpty()) return type == boolean.class ? false : null;
            return Boolean.parseBoolean(value.trim());
        } else if (type == short.class || type == Short.class) {
            if (value.trim().isEmpty()) return type == short.class ? (short) 0 : null;
            return Short.parseShort(value.trim());
        } else if (type == byte.class || type == Byte.class) {
            if (value.trim().isEmpty()) return type == byte.class ? (byte) 0 : null;
            return Byte.parseByte(value.trim());
        } else if (type == char.class || type == Character.class) {
            if (value.isEmpty()) return type == char.class ? '\0' : null;
            return value.charAt(0);
        }
        return value;
    }

    public static Object getDefaultPrimitive(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return null;
    }
}
