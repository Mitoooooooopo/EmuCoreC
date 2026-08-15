package com.sbro.emucorec.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class FirmwareSourceTest {
    @Test
    fun baseFirmwareUsesOfficialSonyRouteWithPinnedArtifactMetadata() {
        val source = FirmwareSources.base

        assertEquals("4.93", source.version)
        assertEquals("deu01.ps3.update.playstation.net", URI(source.officialUrl).host)
        assertEquals("a248.e.akamai.net", URI(source.transportUrl).host)
        assertEquals(URI(source.officialUrl).path, URI(source.transportUrl).path)
        assertEquals(URI(source.officialUrl).host, source.hostHeader)
        assertEquals(206_197_916L, source.exactSizeBytes)
        assertEquals(source.exactSizeBytes, source.approximateSizeBytes)
        assertTrue(source.sha256.matches(Regex("[0-9a-f]{64}")))
        assertEquals("PS3UPDAT.PUP", source.fileName)
    }
}
