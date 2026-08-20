package com.sbro.emucorec.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNativeBackendContractTest {
    @Test
    fun androidCubebUsesTheStableOpenSlBackend() {
        val source = repositoryRoot()
            .resolve("rpcs3/Emu/Audio/Cubeb/CubebBackend.cpp")
            .readText()

        val androidSelection = source.substringAfter("#ifdef __ANDROID__")
            .substringBefore("#endif")

        assertTrue("Android must select Cubeb's OpenSL ES backend", "\"opensl\"" in androidSelection)
        assertTrue("Other platforms must retain Cubeb's automatic backend", "backend_name = nullptr" in source)
        assertTrue("The selected backend must be passed to cubeb_init", "cubeb_init(&ctx, \"RPCS3\", backend_name)" in source)
    }

    @Test
    fun androidRsxCopiesResolveProtectedGuestPagesBeforeMemcpy() {
        val source = repositoryRoot()
            .resolve("rpcs3/Emu/RSX/RSXOffload.cpp")
            .readText()

        assertTrue("Android guest reads need a normal-context protection resolver", "prepare_guest_read" in source)
        assertEquals(
            "Both immediate and offloaded DMA paths must prepare guest memory",
            2,
            Regex("prepare_guest_read\\(vm_addr, (?:job\\.length|length)\\);").findAll(source).count(),
        )

        Regex("prepare_guest_read\\(vm_addr, (?:job\\.length|length)\\);").findAll(source).forEach { call ->
            val followingCopy = source.indexOf("std::memcpy", call.range.last)
            assertTrue("Guest memory must be prepared before the corresponding memcpy", followingCopy > call.range.last)
        }
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        return sequenceOf(workingDirectory, workingDirectory.parent)
            .filterNotNull()
            .firstOrNull { Files.isDirectory(it.resolve("rpcs3/Emu")) }
            ?: error("Unable to locate repository root from $workingDirectory")
    }
}
