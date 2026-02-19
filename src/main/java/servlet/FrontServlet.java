package servlet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

public class FrontServlet extends HttpServlet {

    // init est executé une seule fois au lancement de ce servlet
    @Override
    public void init() throws ServletException {
        try {

        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    // }
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String path = req.getRequestURI();
        ServletContext context = getServletContext();

        // Récupérer le chemin relatif au contexte
        String servletPath = req.getServletPath();
        String realPath = context.getRealPath(servletPath);

        // Si c’est un fichier physique, déléguer au servlet par défaut de Tomcat
        if (realPath != null) {
            java.io.File file = new java.io.File(realPath);
            if (file.exists() && file.isFile()) {
                RequestDispatcher rd = context.getNamedDispatcher("default");
                rd.forward(req, resp);
                return;
            }
        }

        // Sinon → logique applicative
        resp.setContentType("text/plain");
        resp.getWriter().print("FrontServlet → " + path);
    }

}