package dev.formhaus.compose.fields

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.util.UUID

private const val OWNER = 9_001L

@RunWith(RobolectricTestRunner::class)
class UploadStoreTest {
    private val cache = ApplicationProvider.getApplicationContext<Context>().cacheDir
    private lateinit var root: File
    private lateinit var outside: File

    @Before
    fun attach() {
        UploadStore.attach(cache)
        root = UploadStore.directory(cache).canonicalFile.apply { mkdirs() }
        outside = File(cache.canonicalFile, "elsewhere-${UUID.randomUUID()}").apply { mkdirs() }
    }

    private fun file(
        folder: File,
        name: String = "secret.txt",
    ): File = File(folder.apply { mkdirs() }, name).apply { writeText("keep") }

    private fun uuid(): String = UUID.randomUUID().toString()

    private fun assertRejected(
        value: String,
        survivor: File,
    ) {
        assertFalse(value, UploadStore.adopt(value, OWNER))
        assertNull(value, UploadStore.owned(value, OWNER))
        UploadStore.discard(value, OWNER)
        UploadStore.prune(OWNER, emptySet())
        UploadStore.release(OWNER)
        assertTrue(value, survivor.exists())
    }

    @Test
    fun anUploadIsExactlyAFileInAUuidFolderOfTheRoot() {
        val upload = file(File(root, uuid()))
        val value = fileValue(upload)
        assertTrue(UploadStore.isUpload(value))
        assertTrue(UploadStore.adopt(value, OWNER))
        assertEquals(upload, UploadStore.owned(value, OWNER))
        UploadStore.release(OWNER)
        assertFalse(upload.exists())
        assertFalse(upload.parentFile!!.exists())
    }

    @Test
    fun lookalikesTraversalsAndDeeperPathsAreNotUploads() {
        val lookalike = file(File(outside, "formhaus-uploads/${uuid()}"))
        val deeper = file(File(root, "${uuid()}/nested"))
        val named = file(File(root, "not-a-uuid"))
        val target = file(outside)
        val traversal = "file://${root.path}/${uuid()}/../../${outside.name}/${target.name}"
        for ((value, survivor) in listOf(
            fileValue(lookalike) to lookalike,
            fileValue(deeper) to deeper,
            fileValue(named) to named,
            traversal to target,
        )) {
            assertFalse(value, UploadStore.isUpload(value) && UploadStore.exists(value))
            assertRejected(value, survivor)
        }
    }

    @Test
    fun symlinksInsideTheRootAreNotAdopted() {
        val target = file(outside)
        val linkedFile = File(File(root, uuid()).apply { mkdirs() }, "link.txt")
        Files.createSymbolicLink(linkedFile.toPath(), target.toPath())
        val linkedFolder = File(root, uuid())
        Files.createSymbolicLink(linkedFolder.toPath(), outside.toPath())
        for (value in listOf(fileValue(linkedFile), "file://${linkedFolder.path}/${target.name}")) {
            assertFalse(value, UploadStore.exists(value))
            assertRejected(value, target)
        }
        assertTrue(Files.isSymbolicLink(linkedFolder.toPath()))
    }
}
