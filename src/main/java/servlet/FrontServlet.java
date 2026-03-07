package servlet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
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
            // Instancier le controller et invoquer la methode
            Object controllerInstance = mapping.controllerClass.getDeclaredConstructor().newInstance();
            Object result = mapping.method.invoke(controllerInstance);

            // Verifier le type de retour
            Class<?> returnType = mapping.method.getReturnType();

            if (returnType == String.class && result != null) {
                String strResult = (String) result;

                if (strResult.endsWith(".jsp")) {
                    // Si le retour est un chemin JSP, forward vers la JSP
                    RequestDispatcher rd = req.getRequestDispatcher(strResult);
                    rd.forward(req, resp);
                } else {
                    // Sinon, afficher le String directement
                    resp.setContentType("text/plain; charset=UTF-8");
                    resp.getWriter().print(strResult);
                }
            } else {
                // Type de retour non supporte
                resp.setContentType("text/plain; charset=UTF-8");
                resp.setStatus(500);
                resp.getWriter().print("Erreur : le type de retour '"
                        + returnType.getName()
                        + "' de la methode "
                        + mapping.controllerClass.getName() + "#" + mapping.method.getName()
                        + " n'est pas supporte. Seuls String et String(.jsp) sont acceptes.");
            }

        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'invocation de "
                    + mapping.controllerClass.getName() + "#" + mapping.method.getName(), e);
        }
    }
}
