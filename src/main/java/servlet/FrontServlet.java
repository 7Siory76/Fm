package servlet;

import jakarta.servlet.*;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import servlet.annotations.Controller;
import servlet.annotations.GetMapping;
import servlet.annotations.Json;
import servlet.annotations.PostMapping;
import servlet.annotations.RequestMapping;
import servlet.annotations.RequestParam;
import servlet.annotations.Url;
import servlet.util.FileUploadUtils;
import servlet.util.JsonUtils;
import servlet.util.ObjectBinder;

@MultipartConfig
public class FrontServlet extends HttpServlet {

    private static class Mapping {
        Class<?> controllerClass;
        Method method;

        Mapping(Class<?> controllerClass, Method method) {
            this.controllerClass = controllerClass;
            this.method = method;
        }
    }

    private HashMap<String, Mapping> urlMappings = new HashMap<>();

    @Override
    public void init() throws ServletException {
        try {
            List<Class<?>> controllers = scanControllers();

            for (Class<?> clazz : controllers) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(GetMapping.class)) {
                        String url = method.getAnnotation(GetMapping.class).value();
                        urlMappings.put(url + "|GET", new Mapping(clazz, method));
                    }
                    if (method.isAnnotationPresent(PostMapping.class)) {
                        String url = method.getAnnotation(PostMapping.class).value();
                        urlMappings.put(url + "|POST", new Mapping(clazz, method));
                    }
                    if (method.isAnnotationPresent(RequestMapping.class)) {
                        String url = method.getAnnotation(RequestMapping.class).value();
                        urlMappings.put(url + "|*", new Mapping(clazz, method));
                    }
                    if (method.isAnnotationPresent(Url.class)) {
                        String url = method.getAnnotation(Url.class).value();
                        urlMappings.put(url + "|*", new Mapping(clazz, method));
                    }
                }
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private List<Class<?>> scanControllers() throws Exception {
        List<Class<?>> controllers = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        java.util.Enumeration<URL> roots = classLoader.getResources("");
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if (root.getProtocol().equals("file")) {
                File rootDir = new File(root.toURI());
                List<Class<?>> classes = findClasses(rootDir, rootDir);
                for (Class<?> clazz : classes) {
                    if (clazz.isAnnotationPresent(Controller.class)) {
                        controllers.add(clazz);
                    }
                }
            }
        }
        return controllers;
    }

    private List<Class<?>> findClasses(File root, File current) {
        List<Class<?>> classes = new ArrayList<>();
        if (!current.exists()) return classes;
        File[] files = current.listFiles();
        if (files == null) return classes;

        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(findClasses(root, file));
            } else if (file.getName().endsWith(".class")) {
                String relativePath = root.toURI().relativize(file.toURI()).getPath();
                String className = relativePath
                        .replace("/", ".")
                        .replace("\\", ".");
                className = className.substring(0, className.length() - 6);
                try {
                    Class<?> clazz = Class.forName(className);
                    classes.add(clazz);
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                }
            }
        }
        return classes;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String path = req.getRequestURI();
        ServletContext context = getServletContext();

        String servletPath = req.getServletPath();
        String realPath = context.getRealPath(servletPath);

        if (realPath != null) {
            java.io.File file = new java.io.File(realPath);
            if (file.exists() && file.isFile()) {
                RequestDispatcher rd = context.getNamedDispatcher("default");
                rd.forward(req, resp);
                return;
            }
        }

        String contextPath = req.getContextPath();
        String url = path.substring(contextPath.length());
        String httpMethod = req.getMethod().toUpperCase();

        Mapping mapping = urlMappings.get(url + "|" + httpMethod);
        HashMap<String, String> pathVariables = new HashMap<>();

        if (mapping == null) {
            mapping = urlMappings.get(url + "|*");
        }

        if (mapping == null) {
            for (String key : urlMappings.keySet()) {
                int sep = key.lastIndexOf("|");
                String pattern = key.substring(0, sep);
                String keyMethod = key.substring(sep + 1);

                if (!pattern.contains("{")) continue;

                HashMap<String, String> vars = matchUrlPattern(pattern, url);
                if (vars != null) {
                    if (keyMethod.equals(httpMethod) || keyMethod.equals("*")) {
                        if (keyMethod.equals(httpMethod)) {
                            mapping = urlMappings.get(key);
                            pathVariables = vars;
                            break;
                        } else if (mapping == null) {
                            mapping = urlMappings.get(key);
                            pathVariables = vars;
                        }
                    }
                }
            }
        }

        if (mapping == null) {
            resp.setContentType("text/plain");
            resp.getWriter().print("Aucun mapping trouve pour : " + httpMethod + " " + url);
            return;
        }

        boolean isJsonResponse = mapping.method.isAnnotationPresent(Json.class)
                || mapping.controllerClass.isAnnotationPresent(Json.class);

        try {
            Object controllerInstance = mapping.controllerClass.getDeclaredConstructor().newInstance();

            Method method = mapping.method;
            Parameter[] params = method.getParameters();
            Object[] args = new Object[params.length];

            // Traitement des fichiers uploades
            Map<String, List<Upload>> uploadsMap = FileUploadUtils.parseUploads(req, method, mapping.controllerClass);

            for (int i = 0; i < params.length; i++) {
                String paramName;
                if (params[i].isAnnotationPresent(RequestParam.class)) {
                    paramName = params[i].getAnnotation(RequestParam.class).value();
                } else {
                    paramName = params[i].getName();
                }

                Class<?> paramType = params[i].getType();

                // 1. Map (Map<String, List<Upload>> ou Map<String, Object>)
                if (Map.class.isAssignableFrom(paramType)) {
                    if (!uploadsMap.isEmpty()) {
                        args[i] = uploadsMap;
                    } else {
                        HashMap<String, Object> allParams = new HashMap<>();
                        java.util.Enumeration<String> names = req.getParameterNames();
                        while (names.hasMoreElements()) {
                            String name = names.nextElement();
                            allParams.put(name, req.getParameter(name));
                        }
                        allParams.putAll(pathVariables);
                        args[i] = allParams;
                    }
                    continue;
                }

                // 2. Objet Upload individuel
                if (paramType == Upload.class) {
                    if (uploadsMap.containsKey(paramName) && !uploadsMap.get(paramName).isEmpty()) {
                        args[i] = uploadsMap.get(paramName).get(0);
                    } else if (uploadsMap.size() == 1) {
                        args[i] = uploadsMap.values().iterator().next().get(0);
                    } else {
                        args[i] = null;
                    }
                    continue;
                }

                // 3. Tableau Upload[]
                if (paramType == Upload[].class) {
                    if (uploadsMap.containsKey(paramName)) {
                        List<Upload> list = uploadsMap.get(paramName);
                        args[i] = list.toArray(new Upload[0]);
                    } else if (!uploadsMap.isEmpty()) {
                        List<Upload> allUploads = new ArrayList<>();
                        for (List<Upload> uList : uploadsMap.values()) {
                            allUploads.addAll(uList);
                        }
                        args[i] = allUploads.toArray(new Upload[0]);
                    } else {
                        args[i] = new Upload[0];
                    }
                    continue;
                }

                // 4. Liste List<Upload> ou List
                if (List.class.isAssignableFrom(paramType)) {
                    if (uploadsMap.containsKey(paramName)) {
                        args[i] = uploadsMap.get(paramName);
                    } else if (!uploadsMap.isEmpty()) {
                        List<Upload> allUploads = new ArrayList<>();
                        for (List<Upload> uList : uploadsMap.values()) {
                            allUploads.addAll(uList);
                        }
                        args[i] = allUploads;
                    } else {
                        args[i] = new ArrayList<>();
                    }
                    continue;
                }

                // 5. Types simples (String, int, Integer, boolean, Double, etc.)
                if (isSimpleType(paramType)) {
                    String paramValue = null;
                    if (pathVariables.containsKey(paramName)) {
                        paramValue = pathVariables.get(paramName);
                    }
                    if (paramValue == null) {
                        paramValue = req.getParameter(paramName);
                    }

                    if (paramValue != null) {
                        args[i] = ObjectBinder.convertParam(paramValue, paramType);
                    } else {
                        args[i] = ObjectBinder.getDefaultPrimitive(paramType);
                    }
                    continue;
                }

                // 6. Tableaux de primitives/Strings
                if (paramType.isArray()) {
                    Class<?> compType = paramType.getComponentType();
                    if (isSimpleType(compType)) {
                        String[] values = req.getParameterValues(paramName);
                        if (values != null) {
                            Object arr = java.lang.reflect.Array.newInstance(compType, values.length);
                            for (int k = 0; k < values.length; k++) {
                                java.lang.reflect.Array.set(arr, k, ObjectBinder.convertParam(values[k], compType));
                            }
                            args[i] = arr;
                        } else {
                            args[i] = java.lang.reflect.Array.newInstance(compType, 0);
                        }
                    } else {
                        // Tableau d'objets (ex: Employee[] es)
                        args[i] = ObjectBinder.bindArray(compType, paramName, req);
                    }
                    continue;
                }

                // 7. Objet complexe (ex: Employee e, Etudiant etudiant)
                args[i] = ObjectBinder.bindObject(paramType, paramName, req);
            }

            // Invoquer la methode avec les arguments
            Object result = method.invoke(controllerInstance, args);

            // Reponse API REST (JSON)
            if (isJsonResponse) {
                resp.setContentType("application/json; charset=UTF-8");
                String jsonStr = JsonUtils.formatApiResponse(result, 200, "success");
                resp.getWriter().print(jsonStr);
                return;
            }

            // Reponse classique (String / ModelView)
            Class<?> returnType = mapping.method.getReturnType();

            if (returnType == String.class && result != null) {
                String strResult = (String) result;

                if (strResult.endsWith(".jsp")) {
                    req.setAttribute("url", url);
                    RequestDispatcher rd = req.getRequestDispatcher(strResult);
                    rd.forward(req, resp);
                } else {
                    resp.setContentType("text/plain; charset=UTF-8");
                    resp.getWriter().print(strResult);
                }
            } else if (returnType == ModelView.class && result != null) {
                ModelView mv = (ModelView) result;
                String view = mv.getView();

                if (view != null && !view.isEmpty()) {
                    req.setAttribute("url", url);
                    HashMap<String, Object> data = mv.getData();
                    for (String key : data.keySet()) {
                        req.setAttribute(key, data.get(key));
                    }
                    RequestDispatcher rd = req.getRequestDispatcher(view);
                    rd.forward(req, resp);
                } else {
                    resp.setContentType("text/plain; charset=UTF-8");
                    resp.setStatus(500);
                    resp.getWriter().print("Erreur : ModelView retourne avec une vue null ou vide.");
                }
            } else {
                resp.setContentType("text/plain; charset=UTF-8");
                resp.setStatus(500);
                resp.getWriter().print("Erreur : le type de retour '"
                        + returnType.getName()
                        + "' de la methode "
                        + mapping.controllerClass.getName() + "#" + mapping.method.getName()
                        + " n'est pas supporte. Seuls String, String(.jsp) et ModelView sont acceptes.");
            }

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (isJsonResponse) {
                resp.setContentType("application/json; charset=UTF-8");
                resp.setStatus(500);
                try {
                    resp.getWriter().print(JsonUtils.formatApiError(500, cause.getMessage()));
                } catch (IOException ignored) {}
                return;
            }
            throw new ServletException("Erreur lors de l'invocation de "
                    + mapping.controllerClass.getName() + "#" + mapping.method.getName(), e);
        }
    }

    private boolean isSimpleType(Class<?> type) {
        return type == String.class || type.isPrimitive() ||
               type == Integer.class || type == Long.class ||
               type == Double.class || type == Float.class ||
               type == Boolean.class || type == Short.class ||
               type == Byte.class || type == Character.class ||
               type.isEnum();
    }

    private HashMap<String, String> matchUrlPattern(String pattern, String url) {
        if (!pattern.contains("{")) return null;

        String[] patternParts = pattern.split("/");
        String[] urlParts = url.split("/");

        if (patternParts.length != urlParts.length) return null;

        HashMap<String, String> variables = new HashMap<>();

        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                String varName = patternParts[i].substring(1, patternParts[i].length() - 1);
                variables.put(varName, urlParts[i]);
            } else if (!patternParts[i].equals(urlParts[i])) {
                return null;
            }
        }
        return variables;
    }
}
