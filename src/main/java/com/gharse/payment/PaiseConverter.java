package com.gharse.payment;

import java.math.BigDecimal;

/**
 * Converts between rupee amounts (BigDecimal, as stored in DECIMAL(10,2) columns) and the
 * integer paise amounts Razorpay expects and returns.
 *
 * <p>Uses exact decimal arithmetic only. Never route money through double/float.
 */
public final class PaiseConverter {

    private PaiseConverter() {
    }

    /**
     * @param rupees amount in rupees, must be positive and have at most 2 decimal places
     * @return the amount in paise (Rs 499.00 -> 49900)
     * @throws IllegalArgumentException if null, not positive, or has fractional paise
     */
    public static long toPaise(BigDecimal rupees) {
        if (rupees == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (rupees.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        try {
            return rupees.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Amount must have at most 2 decimal places: " + rupees.toPlainString(), e);
        }
    }

    /**
     * @param paise amount in paise (for example from a Razorpay webhook payload)
     * @return the amount in rupees with scale 2 (49900 -> 499.00)
     */
    public static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }
}