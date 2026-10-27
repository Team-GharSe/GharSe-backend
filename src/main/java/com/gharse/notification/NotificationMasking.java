package com.gharse.notification;

final class NotificationMasking {

    private NotificationMasking() {}

    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "(none)";
        }
        String p = phone.trim();
        if (p.length() <= 4) {
            return "****";
        }
        return "*".repeat(p.length() - 4) + p.substring(p.length() - 4);
    }

    static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "(none)";
        }
        String e = email.trim();
        int at = e.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return e.charAt(0) + "***" + e.substring(at);
    }
}