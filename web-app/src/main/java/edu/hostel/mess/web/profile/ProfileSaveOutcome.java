package edu.hostel.mess.web.profile;

/** Post-save navigation and one-time dashboard feedback for profile updates. */
public record ProfileSaveOutcome(boolean complete, String redirectPath, String flashCode) {
    public static final String SESSION_ATTRIBUTE = ProfileSaveOutcome.class.getName() + ".flash";
    public static final String COMPLETED = "profile-completed";
    public static final String UPDATED = "profile-updated";

    public static ProfileSaveOutcome afterSave(boolean wasComplete, UserProfile profile) {
        if (profile == null || !profile.isComplete()) {
            return new ProfileSaveOutcome(false, "/profile", null);
        }
        return new ProfileSaveOutcome(true, "/dashboard", wasComplete ? UPDATED : COMPLETED);
    }

    public static String dashboardMessage(String code) {
        if (COMPLETED.equals(code)) return "Profile completed successfully.";
        if (UPDATED.equals(code)) return "Profile updated.";
        return null;
    }
}
