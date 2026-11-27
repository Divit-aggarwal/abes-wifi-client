package com.divit.abesclient

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginThrottleTest {
    @Test fun capsAttempts() {
        val t = LoginThrottle()
        assertTrue(t.allow(0))
        assertFalse(t.allow(10_000))      // too soon
        assertTrue(t.allow(40_000))
        assertTrue(t.allow(80_000))
        assertFalse(t.allow(120_000))     // 3 in the window
        assertTrue(t.allow(640_000))      // first one aged out
    }
}
