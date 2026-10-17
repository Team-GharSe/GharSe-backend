package com.gharse.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaiseConverterTest {

    @Test
    void convertsWholeAndFractionalRupees() {
        assertEquals(49900L, PaiseConverter.toPaise(new BigDecimal("499.00")));
        assertEquals(1050L, PaiseConverter.toPaise(new BigDecimal("10.5")));
        assertEquals(1L, PaiseConverter.toPaise(new BigDecimal("0.01")));
        assertEquals(123456789L, PaiseConverter.toPaise(new BigDecimal("1234567.89")));
    }

    @Test
    void trailingZerosBeyondTwoDecimalsAreFine() {
        assertEquals(1050L, PaiseConverter.toPaise(new BigDecimal("10.500")));
        assertEquals(1000L, PaiseConverter.toPaise(new BigDecimal("10")));
    }

    @Test
    void doesNotSufferFromFloatingPointError() {
        // 0.1 + 0.2 as double is 0.30000000000000004; as BigDecimal it must be exactly 30 paise.
        BigDecimal sum = new BigDecimal("0.10").add(new BigDecimal("0.20"));
        assertEquals(30L, PaiseConverter.toPaise(sum));
        assertEquals(1999L, PaiseConverter.toPaise(new BigDecimal("19.99")));
    }

    @Test
    void rejectsNullZeroAndNegative() {
        assertThrows(IllegalArgumentException.class, () -> PaiseConverter.toPaise(null));
        assertThrows(IllegalArgumentException.class, () -> PaiseConverter.toPaise(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> PaiseConverter.toPaise(new BigDecimal("-5.00")));
    }

    @Test
    void rejectsFractionalPaise() {
        assertThrows(IllegalArgumentException.class, () -> PaiseConverter.toPaise(new BigDecimal("10.005")));
        assertThrows(IllegalArgumentException.class, () -> PaiseConverter.toPaise(new BigDecimal("0.001")));
    }

    @Test
    void convertsPaiseBackToRupeesWithScaleTwo() {
        assertEquals(new BigDecimal("499.00"), PaiseConverter.fromPaise(49900L));
        assertEquals(new BigDecimal("0.01"), PaiseConverter.fromPaise(1L));
        assertEquals(0, new BigDecimal("10.50").compareTo(PaiseConverter.fromPaise(1050L)));
    }

    @Test
    void roundTripIsLossless() {
        BigDecimal rupees = new BigDecimal("2599.75");
        assertEquals(0, rupees.compareTo(PaiseConverter.fromPaise(PaiseConverter.toPaise(rupees))));
    }
}