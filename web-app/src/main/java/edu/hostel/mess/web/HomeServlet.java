package edu.hostel.mess.web;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import edu.hostel.mess.web.security.AuthenticatedUser;
import edu.hostel.mess.web.security.SecurityFilter;
import java.time.Year;

@WebServlet("")
public class HomeServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-cache");
        request.setAttribute("currentYear", Year.now().getValue());
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute(SecurityFilter.USER_SESSION_ATTRIBUTE);
        request.setAttribute("signedIn", user instanceof AuthenticatedUser);
        request.getRequestDispatcher("/WEB-INF/views/landing.jsp").forward(request, response);
    }
}
