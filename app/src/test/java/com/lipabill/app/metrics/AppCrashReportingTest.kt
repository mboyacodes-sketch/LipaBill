package com.lipabill.app.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCrashReportingTest {

    @Test
    fun scrub_keeps_cause_chain_and_app_frames() {
        val root = IllegalStateException("paid 0712345678 KES 500")
        root.stackTrace = arrayOf(
            StackTraceElement("com.lipabill.app.MainActivity", "onCreate", "MainActivity.kt", 140)
        )
        val wrapped = RuntimeException("Unable to start activity ComponentInfo{com.lipabill.app/.MainActivity}", root)
        wrapped.stackTrace = arrayOf(
            StackTraceElement("android.app.ActivityThread", "performLaunchActivity", "ActivityThread.java", 3413)
        )

        val safe = scrubThrowableForReporting(wrapped)
        assertEquals("java.lang.RuntimeException", safe.message)
        assertEquals("android.app.ActivityThread", safe.stackTrace.first().className)
        val cause = safe.cause
        assertNotNull(cause)
        assertEquals("java.lang.IllegalStateException", cause!!.message)
        assertEquals("com.lipabill.app.MainActivity", cause.stackTrace.first().className)
        assertTrue("0712" !in (safe.message + cause.message))
        assertTrue("KES" !in (safe.message + cause.message))
    }

    @Test
    fun sanitize_token_strips_digits() {
        assertEquals("pay:start:send", sanitizeToken("pay:start:send"))
        assertEquals("paid_KES", sanitizeToken("paid 0712345678 KES 500"))
        assertEquals(null, sanitizeToken("   "))
        assertEquals(null, sanitizeToken("0712345678"))
    }
}
