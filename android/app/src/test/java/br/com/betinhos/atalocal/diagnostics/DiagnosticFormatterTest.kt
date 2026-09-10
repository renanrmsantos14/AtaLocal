package br.com.betinhos.atalocal.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticFormatterTest {
    @Test fun formatsStorageUsingBinaryUnits() {
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
    }
}
