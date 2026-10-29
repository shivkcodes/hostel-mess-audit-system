package edu.hostel.mess.web.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecurityPolicyTest {
    @Test
    void signInSignupAndStaticAssetsArePublic() {
        assertTrue(SecurityPolicy.isPublicPath("/login"));
        assertTrue(SecurityPolicy.isPublicPath("/signup"));
        assertTrue(SecurityPolicy.isPublicPath("/register"));
        assertTrue(SecurityPolicy.isPublicPath(""));
        assertTrue(SecurityPolicy.isPublicPath("/dashboard.css"));
        assertTrue(SecurityPolicy.isPublicPath("/js/analytics-charts.js"));
        assertTrue(SecurityPolicy.isPublicPath("/js/auth.js"));
        assertTrue(SecurityPolicy.isPublicPath("/js/database-status-indicator.js"));
        assertTrue(SecurityPolicy.isPublicPath("/js/admin-data-filter.js"));
        assertFalse(SecurityPolicy.isPublicPath("/dashboard"));
        assertFalse(SecurityPolicy.isPublicPath("/database-status"));
    }

    @Test
    void diagnosticsAndStateChangesRequireAdministrator() {
        assertTrue(SecurityPolicy.requiresAdmin("/database-status", "GET"));
        assertTrue(SecurityPolicy.requiresAdmin("/database-status/check", "GET"));
        assertTrue(SecurityPolicy.requiresAdmin("/database-status/indicator", "GET"));
        assertTrue(SecurityPolicy.requiresAdmin("/procurement", "POST"));
        assertTrue(SecurityPolicy.requiresAdmin("/goods-receipts", "POST"));
        assertTrue(SecurityPolicy.requiresAdmin("/vendors", "POST"));
        assertTrue(SecurityPolicy.requiresAdmin("/ingredients", "POST"));
        assertTrue(SecurityPolicy.requiresAdmin("/mess-blocks", "POST"));
        assertTrue(SecurityPolicy.requiresAdmin("/admin/data-management", "GET"));
        assertTrue(SecurityPolicy.requiresAdmin("/admin/data-cleanup", "POST"));
        assertFalse(SecurityPolicy.requiresAdmin("/logout", "POST"));
        assertFalse(SecurityPolicy.requiresAdmin("/signup", "POST"));
        assertFalse(SecurityPolicy.requiresAdmin("/inventory", "GET"));
    }

    @Test
    void onlyKnownPostRoutesCanChangeState() {
        assertTrue(SecurityPolicy.allowsStateChange("/login", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/signup", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/logout", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/procurement", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/goods-receipts", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/vendors", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/ingredients", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/mess-blocks", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/admin/data-management", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/admin/data-cleanup", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/profile", "POST"));
        assertFalse(SecurityPolicy.allowsStateChange("/reference-admin", "POST"));
        assertFalse(SecurityPolicy.allowsStateChange("/dashboard", "POST"));
        assertFalse(SecurityPolicy.allowsStateChange("/procurement", "GET"));
        assertFalse(SecurityPolicy.allowsStateChange("/profile", "GET"));
    }

    @Test
    void profilePostIsAnAuthenticatedSelfServiceMutationForEitherRole() {
        assertFalse(SecurityPolicy.requiresAdmin("/profile", "GET"));
        assertFalse(SecurityPolicy.requiresAdmin("/profile", "POST"));
        assertTrue(SecurityPolicy.allowsStateChange("/profile", "POST"));
        assertTrue(SecurityPolicy.requiresAdmin("/admin/data-management", "POST"));
    }

    @Test
    void returnPathAllowsOnlyKnownLocalRoutes() {
        assertEquals("/inventory", SafeReturnPath.normalize("/inventory"));
        assertEquals("/profile", SafeReturnPath.normalize("/profile"));
        assertNull(SafeReturnPath.normalize("https://example.invalid"));
        assertNull(SafeReturnPath.normalize("//example.invalid"));
        assertNull(SafeReturnPath.normalize("/login?next=https://example.invalid"));
        assertNull(SafeReturnPath.normalize("/unknown"));
    }
}
