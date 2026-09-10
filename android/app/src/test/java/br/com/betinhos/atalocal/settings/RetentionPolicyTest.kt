package br.com.betinhos.atalocal.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {
    @Test fun deletesOnlyDataOlderThanConfiguredDays() {
        val policy = RetentionPolicy(days = 30)
        assertTrue(policy.shouldDelete(now = 100, createdAt = 69))
        assertFalse(policy.shouldDelete(now = 100, createdAt = 70))
    }

    @Test fun disabledRetentionNeverDeletes() {
        assertFalse(RetentionPolicy(null).shouldDelete(now = 100, createdAt = 0))
    }
}
