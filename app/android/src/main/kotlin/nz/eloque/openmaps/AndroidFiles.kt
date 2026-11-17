package nz.eloque.openmaps

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import nz.eloque.openmaps.core.platform.Files
import java.io.File

/** [launchPicker] is set by the activity that can show the system file picker, which reports back through [onPicked]. */
class AndroidFiles(
    private val context: Context,
) : Files {
    var launchPicker: (() -> Unit)? = null
    private var pending: CompletableDeferred<Uri?>? = null
    private val openedFiles = Channel<String>(Channel.BUFFERED)

    override val opened: Flow<String> = openedFiles.receiveAsFlow()

    override suspend fun pickText(): String? {
        val launch = launchPicker ?: return null
        pending?.complete(null)
        val result = CompletableDeferred<Uri?>().also { pending = it }
        launch()
        val uri = result.await() ?: return null
        return read(uri)
    }

    fun onPicked(uri: Uri?) {
        pending?.complete(uri)
        pending = null
    }

    suspend fun open(uri: Uri) {
        read(uri)?.let(openedFiles::trySend)
    }

    private suspend fun read(uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        }

    override fun share(
        name: String,
        mimeType: String,
        text: String,
    ) {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val file = File(dir, name.replace(UnsafeFileCharacters, "_")).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType(mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}

private const val SHARED_DIR = "shared"
private val UnsafeFileCharacters = Regex("""[^\p{L}\p{N}._ -]""")
