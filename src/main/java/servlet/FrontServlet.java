package servlet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import servlet.annotations.Controller;
import servlet.annotations.Url;

public class FrontServlet extends HttpServlet {

    // Classe interne pour stocker le mapping complet
    private static class Mapping {
        Class<?> controllerClass;
        Method method;

        Mapping(Class<?> controllerClass, Method method) {
            this.controllerClass = controllerClass;
            this.method = method;
        }
    }

    // Stockage : url -> Mapping (classe + methode)
    private HashMap<String, Mapping> urlMappings = new HashMap<>();

    @Override
    public void init() throws ServletException {
        try {
            List<Class<?>> controllers = scanControllers();

            for (Class<?> clazz : controllers) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Url.class)) {
                        Url urlAnnotation = method.getAnnotation(Url.class);
                        String url = urlAnnotation.value();
                        urlMappings.put(url, new Mapping(clazz, method));
                    }
                }
            }

        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    // Scanner tout le classpath pour trouver les classes annotees @Controller
    private List<Class<?>> scanControllers() throws Exception {
        List<Class<?>> controllers = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        // Parcourir toutes les racines du classpath
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

    // Trouver recursivement toutes les classes dans un repertoire
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
                    // Ignorer les classes non chargeables
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

        // Recuperer le chemin relatif au contexte
        String servletPath = req.getServletPath();
        String realPath = context.getRealPath(servletPath);

        // Si c'est un fichier physique, deleguer au servlet par defaut de Tomcat
        if (realPath != null) {
            java.io.File file = new java.io.File(realPath);
            if (file.exists() && file.isFile()) {
                RequestDispatcher rd = context.getNamedDispatcher("default");
                rd.forward(req, resp);
                return;
            }
        }

        // Extraire l'URL relative (sans le context path)
        String contextPath = req.getContextPath();
        String url = path.substring(contextPath.length());

        if (!urlMappings.containsKey(url)) {
            resp.setContentType("text/plain");
            resp.getWriter().print("Aucun mapping trouve pour : " + url);
            return;
        }

        Mapping mapping = urlMappings.get(url);
        try {
            // Instancier le controller
            Object controllerInstance = mapping.controllerClass.getDeclaredConstructor().newInstance();

            // Preparer les arguments de la methode a partir des parametres de la request
            Method method = mapping.method;
            Parameter[] params = method.getParameters();
            Object[] args = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                String paramName = params[i].getName();
                String paramValue = req.getParameter(paramName);
                Class<?> paramType = params[i].getType();

                if (paramValue != null) {
                    args[i] = convertParam(paramValue, paramType);
                } else {
                    // Pas de valeur -> null pour les objets, valeur par defaut pour les primitifs
                    if (paramType.isPrimitive()) {
                        args[i] = getDefaultPrimitive(paramType);
                    } else {
                        args[i] = null;
                    }
                }
            }

            // Invoquer la methode avec les arguments
            Object result = method.invoke(controllerInstance, args);

            // Verifier le type de retour
            Class<?> returnType = mapping.method.getReturnType();

            if (returnType == String.class && result != null) {
                String strResult = (String) result;

                if (strResult.endsWith(".jsp")) {
                    // Si le retour est un chemin JSP, forward vers la JSP
                    req.setAttribute("url", url);
                    RequestDispatcher rd = req.getRequestDispatcher(strResult);
                    rd.forward(req, resp);
                } else {
                    // Sinon, afficher le String directement
                    resp.setContentType("text/plain; charset=UTF-8");
                    resp.getWriter().print(strResult);
                }
            } else if (returnType == ModelView.class && result != null) {
                // Retour ModelView -> forward vers la vue
                ModelView mv = (ModelView) result;
                String view = mv.getView();

                if (view != null && !view.isEmpty()) {
                    req.setAttribute("url", url);
                    // Transferer toutes les donnees du ModelView en attributs de la request
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
                // Type de retour non supporte
                resp.setContentType("text/plain; charset=UTF-8");
                resp.setStatus(500);
                resp.getWriter().print("Erreur : le type de retour '"
                        + returnType.getName()
                        + "' de la methode "
                        + mapping.controllerClass.getName() + "#" + mapping.method.getName()
                        + " n'est pas supporte. Seuls String, String(.jsp) et ModelView sont acceptes.");
            }

        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'invocation de "
                    + mapping.controllerClass.getName() + "#" + mapping.method.getName(), e);
        }
    }

    // Convertir une valeur String en le type attendu
    private Object convertParam(String value, Class<?> type) {
        if (type == String.class) {
            return value;
        } else if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        } else if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        } else if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        } else if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    // Valeur par defaut pour les types primitifs
    private Object getDefaultPrimitive(Class<?> type) {
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
