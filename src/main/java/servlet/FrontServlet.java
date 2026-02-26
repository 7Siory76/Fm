package servlet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import servlet.annotations.Controller;
import servlet.annotations.Url;

public class FrontServlet extends HttpServlet {

    // Stockage : url -> "ClassName#methodName"
    private HashMap<String, String> urlMappings = new HashMap<>();

    @Override
    public void init() throws ServletException {
        try {
            // Scanner tout le classpath pour trouver les @Controller
            List<Class<?>> controllers = scanControllers();

            // Pour chaque @Controller, chercher les methodes @Url
            for (Class<?> clazz : controllers) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Url.class)) {
                        Url urlAnnotation = method.getAnnotation(Url.class);
                        String url = urlAnnotation.value();
                        String mapping = clazz.getName() + "#" + method.getName();
                        urlMappings.put(url, mapping);
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

        resp.setContentType("text/plain");

        if (urlMappings.containsKey(url)) {
            resp.getWriter().print("URL trouvee : " + url + " -> " + urlMappings.get(url));
        } else {
            resp.getWriter().print("Aucun mapping trouve pour : " + url
                    + "\nMappings disponibles : " + urlMappings.toString());
        }
    }
}
