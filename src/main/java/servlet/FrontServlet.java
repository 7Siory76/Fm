package servlet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import servlet.annotations.Url;

public class FrontServlet extends HttpServlet {

    // Stockage des URLs detectees : url -> "ClassName#methodName"
    private HashMap<String, String> urlMappings = new HashMap<>();

    // init est execute une seule fois au lancement de ce servlet
    @Override
    public void init() throws ServletException {
        try {
            // Lire le package des controleurs depuis les init-params
            String controllerPackage = this.getInitParameter("controller-package");
            if (controllerPackage != null && !controllerPackage.isEmpty()) {
                scanControllers(controllerPackage);
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    // Scanner un package pour trouver les methodes annotees avec @Url
    private void scanControllers(String packageName) throws Exception {
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        java.util.Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.toURI()));
        }

        for (File directory : dirs) {
            List<Class<?>> classes = findClasses(directory, packageName);
            for (Class<?> clazz : classes) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Url.class)) {
                        Url urlAnnotation = method.getAnnotation(Url.class);
                        String url = urlAnnotation.value();
                        String mapping = clazz.getName() + "#" + method.getName();
                        urlMappings.put(url, mapping);
                    }
                }
            }
        }
    }

    // Trouver recursivement toutes les classes dans un repertoire
    private List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        if (files == null) return classes;

        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                classes.add(Class.forName(className));
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

        // Sinon -> logique applicative
        // Extraire l'URL relative (sans le context path)
        String contextPath = req.getContextPath();
        String url = path.substring(contextPath.length());

        resp.setContentType("text/plain");

        // Verifier si l'URL correspond a un mapping detecte
        if (urlMappings.containsKey(url)) {
            resp.getWriter().print("URL trouvee : " + url + " -> " + urlMappings.get(url));
        } else {
            resp.getWriter().print("Aucun mapping trouve pour : " + url
                    + "\nMappings disponibles : " + urlMappings.toString());
        }
    }

}
