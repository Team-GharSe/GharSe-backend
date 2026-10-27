package com.gharse.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationMaskingTest {

    @Test
    void phoneKeepsOnlyTheLastFourDigits() {
        assertEquals("******0001", NotificationMasking.maskPhone("9000000001"));
    }

    @Test
    void shortOrMissingPhoneIsFullyHidden() {
        assertEquals("****", NotificationMasking.maskPhone("123"));
        assertEquals("(none)", NotificationMasking.maskPhone(null));
        assertEquals("(none)", NotificationMasking.maskPhone("  "));
    }

    @Test
    void emailKeepsFirstLetterAndDomain() {
        assertEquals("a***@example.com", NotificationMasking.maskEmail("asha@example.com"));
    }

    @Test
    void malformedOrMissingEmailIsHidden() {
        assertEquals("***", NotificationMasking.maskEmail("nope"));
        assertEquals("***", NotificationMasking.maskEmail("@x.com"));
        assertEquals("(none)", NotificationMasking.maskEmail(null));
    }
}