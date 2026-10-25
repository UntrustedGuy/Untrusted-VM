package untrusted.vm.rom

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SafePathsTest {
    private val base = File("/tmp/base")

    @Test fun clampStaysInsideBase() {
        assertEquals(File(base, "etc/passwd"), SafePaths.clampGuestPath(base, "/etc/passwd"))
        assertEquals(File(base, "etc/passwd"), SafePaths.clampGuestPath(base, "../../../etc/passwd"))
        assertEquals(File(base, "b"), SafePaths.clampGuestPath(base, "/a/../b"))
        assertEquals(base, SafePaths.clampGuestPath(base, "/../.."))
        assertEquals(base, SafePaths.clampGuestPath(base, ""))
        assertEquals(base, SafePaths.clampGuestPath(base, null))
    }

    @Test fun normalizeRejectsHostileNames() {
        assertNull(SafePaths.normalizeEntryName("../evil"))
        assertNull(SafePaths.normalizeEntryName("a/../../evil"))
        assertNull(SafePaths.normalizeEntryName("/abs/path"))
        assertNull(SafePaths.normalizeEntryName("C:/windows"))
        assertNull(SafePaths.normalizeEntryName("..\\evil"))
        assertNull(SafePaths.normalizeEntryName("a\u0000b"))
        assertEquals("system/bin/sh", SafePaths.normalizeEntryName("./system//bin/sh"))
        assertEquals("system", SafePaths.normalizeEntryName("system/"))
    }

    @Test fun resolveInsideBlocksSymlinkEscape() {
        val tmp = Files.createTempDirectory("sp").toFile()
        try {
            val outside = Files.createTempDirectory("outside").toFile()
            val base = File(tmp, "base").apply { mkdirs() }
            Files.createSymbolicLink(File(base, "link").toPath(), outside.toPath())
            assertNull(SafePaths.resolveInside(base, "link/pwned.txt"))
            assertNotNull(SafePaths.resolveInside(base, "ok/file.txt"))
            outside.deleteRecursively()
        } finally { tmp.deleteRecursively() }
    }

    @Test fun deleteTreeDoesNotFollowSymlinks() {
        val tmp = Files.createTempDirectory("dt").toFile()
        try {
            val outside = File(tmp, "outside").apply { mkdirs() }
            File(outside, "keep.txt").writeText("x")
            val tree = File(tmp, "tree").apply { mkdirs() }
            Files.createSymbolicLink(File(tree, "link").toPath(), outside.toPath())
            Files.createSymbolicLink(File(tree, "dangling").toPath(), File("/nonexistent/target").toPath())
            File(tree, "real.txt").writeText("y")
            assertTrue(SafePaths.isSymlink(File(tree, "link")))
            assertFalse(SafePaths.isSymlink(File(tree, "real.txt")))
            assertTrue(SafePaths.deleteTree(tree))
            assertFalse(tree.exists())
            assertTrue("target of symlink must survive", File(outside, "keep.txt").exists())
        } finally { SafePaths.deleteTree(tmp) }
    }
}
