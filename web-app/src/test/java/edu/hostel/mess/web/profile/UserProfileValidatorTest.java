package edu.hostel.mess.web.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserProfileValidatorTest {
    @Test
    void acceptsRequiredProfileAndPreservesOptionalNulls() throws Exception {
        UserProfile profile = UserProfileValidator.validate(new UserProfileValidator.FormData(
                "  Alex Student ", "alex@example.edu", "", "", "", "", "", "Example College",
                "MCA", "", "2", "1", "", ""));

        assertEquals("Alex Student", profile.fullName());
        assertEquals(2, profile.semesterNumber());
        assertNull(profile.phoneNumber());
        assertNull(profile.enrollmentId());
        assertTrue(profile.isComplete());
    }

    @Test
    void requiresValidEmailAndSemesterWithinSupportedRange() {
        assertThrows(UserProfileValidator.ValidationException.class, () -> UserProfileValidator.validate(
                new UserProfileValidator.FormData("Alex", "not-an-email", "", "", "", "", "",
                        "College", "MCA", "", "1", "", "", "")));
        assertThrows(UserProfileValidator.ValidationException.class, () -> UserProfileValidator.validate(
                new UserProfileValidator.FormData("Alex", "alex@example.edu", "", "", "", "", "",
                        "College", "MCA", "", "13", "", "", "")));
    }

    @Test
    void rejectsOverlongAndControlCharacterFields() {
        assertThrows(UserProfileValidator.ValidationException.class, () -> UserProfileValidator.validate(
                new UserProfileValidator.FormData("A".repeat(121), "alex@example.edu", "", "", "", "", "",
                        "College", "MCA", "", "1", "", "", "")));
        assertThrows(UserProfileValidator.ValidationException.class, () -> UserProfileValidator.validate(
                new UserProfileValidator.FormData("Alex\nStudent", "alex@example.edu", "", "", "", "", "",
                        "College", "MCA", "", "1", "", "", "")));
    }

    @Test
    void reportsRequiredFieldsStillMissingFromAnIncompleteForm() {
        UserProfileValidator.FormData form = new UserProfileValidator.FormData(
                "", "", "", "", "", "", "", "", "", "", "", "", "", "");

        assertEquals(java.util.List.of("Full name", "Email address", "College or institution",
                "Course or program", "Current semester"), UserProfileValidator.missingRequiredFields(form));
    }

    @Test
    void initialCompletionAndLaterEditChooseDifferentDashboardMessages() throws Exception {
        UserProfile completed = UserProfileValidator.validate(new UserProfileValidator.FormData(
                "Alex Student", "alex@example.edu", "", "", "", "", "", "Example College",
                "MCA", "", "2", "", "", ""));

        ProfileSaveOutcome firstSave = ProfileSaveOutcome.afterSave(false, completed);
        ProfileSaveOutcome laterSave = ProfileSaveOutcome.afterSave(true, completed);

        assertEquals("/dashboard", firstSave.redirectPath());
        assertEquals(ProfileSaveOutcome.COMPLETED, firstSave.flashCode());
        assertEquals("Profile completed successfully.", ProfileSaveOutcome.dashboardMessage(firstSave.flashCode()));
        assertEquals("/dashboard", laterSave.redirectPath());
        assertEquals(ProfileSaveOutcome.UPDATED, laterSave.flashCode());
        assertEquals("Profile updated.", ProfileSaveOutcome.dashboardMessage(laterSave.flashCode()));
    }

    @Test
    void incompleteProfileHasNoSuccessRedirectOrFlash() {
        UserProfile incomplete = new UserProfile("Alex", "alex@example.edu", null, null, null,
                null, null, null, null, null, null, null, null, null);
        ProfileSaveOutcome outcome = ProfileSaveOutcome.afterSave(false, incomplete);

        assertTrue(!outcome.complete());
        assertEquals("/profile", outcome.redirectPath());
        assertNull(outcome.flashCode());
    }
}
