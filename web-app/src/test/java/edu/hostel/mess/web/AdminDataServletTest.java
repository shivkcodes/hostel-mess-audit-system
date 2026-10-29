package edu.hostel.mess.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import jakarta.servlet.annotation.WebServlet;

class AdminDataServletTest {
    @Test
    void mapsBothAdminGetRoutesToTheSharedServlet() {
        WebServlet mapping = AdminDataServlet.class.getAnnotation(WebServlet.class);

        assertArrayEquals(new String[] { "/admin/data-management", "/admin/data-cleanup" },
                mapping.urlPatterns());
    }

    @Test
    void missingOrUnknownFlashCodeDoesNotBreakAnInitialGet() {
        AdminDataServlet servlet = new AdminDataServlet();

        assertNull(servlet.successMessage(null));
        assertNull(servlet.successMessage("not-a-known-result"));
    }

    @Test
    void recognizedFlashCodeStillProducesItsMessage() {
        assertEquals("Daily mess record saved.", new AdminDataServlet().successMessage("daily-saved"));
    }

    @Test
    void selectedCategoryIsValidatedAndDefaultsByPage() {
        assertEquals("reference", AdminDataServlet.selectedSection(null, false));
        assertEquals("orders", AdminDataServlet.selectedSection(null, true));
        assertEquals("accounts", AdminDataServlet.selectedSection("accounts", false));
        assertEquals("receipts", AdminDataServlet.selectedSection("receipts", true));
        assertEquals("orders", AdminDataServlet.selectedSection("audit", true));
        assertEquals("orders", AdminDataServlet.selectedSection("unexpected", true));
    }
}
