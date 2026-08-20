package com.sbro.emucorec.core

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallContentPlannerTest {
    @Test
    fun numberedPkgExtensionsBecomeOneNativeInstallUnit() = withFiles(
        "game.pkg.10",
        "game.pkg.2",
        "game.pkg.1",
        "license.rap",
    ) { files ->
        val plan = InstallContentPlanner.create(files.map { it.absolutePath })

        assertTrue(plan.isSplitPackage)
        assertEquals(listOf("game.pkg.1", "game.pkg.2", "game.pkg.10"), plan.payloads.map { it.name })
        assertEquals(listOf("license.rap"), plan.licences.map { it.name })
        assertEquals(2, plan.totalUnits)
    }

    @Test
    fun partSuffixPackagesBecomeOneNativeInstallUnit() = withFiles(
        "release_part2.pkg",
        "release_part1.pkg",
    ) { files ->
        val plan = InstallContentPlanner.create(files.map { it.absolutePath })

        assertTrue(plan.isSplitPackage)
        assertEquals(listOf("release_part1.pkg", "release_part2.pkg"), plan.payloads.map { it.name })
        assertEquals(1, plan.totalUnits)
    }

    @Test
    fun unrelatedPackagesRemainIndependentInstallUnits() = withFiles(
        "base-game.pkg",
        "update.pkg",
    ) { files ->
        val plan = InstallContentPlanner.create(files.map { it.absolutePath })

        assertFalse(plan.isSplitPackage)
        assertEquals(2, plan.totalUnits)
    }

    @Test
    fun missingAndDuplicatePathsAreIgnored() = withFiles("game.pkg") { files ->
        val existing = files.single()
        val plan = InstallContentPlanner.create(
            listOf(existing.absolutePath, existing.absolutePath, existing.parentFile!!.resolve("missing.pkg").absolutePath)
        )

        assertEquals(listOf(existing.canonicalPath), plan.payloads.map { it.canonicalPath })
        assertEquals(1, plan.totalUnits)
    }

    private fun withFiles(vararg names: String, block: (List<java.io.File>) -> Unit) {
        val root = Files.createTempDirectory("emucorec-install-plan").toFile()
        try {
            val files = names.map { root.resolve(it).apply { writeBytes(byteArrayOf(1)) } }
            block(files)
        } finally {
            root.deleteRecursively()
        }
    }
}
