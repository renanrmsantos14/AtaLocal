package br.com.betinhos.atalocal.models

import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidModelCatalogTest {
    @Test fun everyCatalogModelHasPinnedIntegrityMetadata() {
        assertTrue(AndroidModelCatalog.all.all { it.sha256.matches(Regex("[0-9a-f]{64}")) && it.sizeBytes > 0 })
    }
}
