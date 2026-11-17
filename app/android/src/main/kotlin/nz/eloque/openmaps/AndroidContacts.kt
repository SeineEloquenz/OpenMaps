package nz.eloque.openmaps

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.eloque.openmaps.core.platform.Contacts

/** [launchPicker] is set by the activity that can show the contact picker, which reports back through [onPicked]. */
class AndroidContacts(
    private val context: Context,
) : Contacts {
    var launchPicker: (() -> Boolean)? = null
    private var pending: CompletableDeferred<Uri?>? = null

    override suspend fun pickAddress(): String? {
        val launch = launchPicker ?: return null
        pending?.complete(null)
        val result = CompletableDeferred<Uri?>().also { pending = it }
        if (!launch()) {
            pending = null
            return null
        }
        val uri = result.await() ?: return null
        return withContext(Dispatchers.IO) { runCatching { read(uri) }.getOrNull() }
    }

    fun onPicked(uri: Uri?) {
        pending?.complete(uri)
        pending = null
    }

    private fun read(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(StructuredPostal.FORMATTED_ADDRESS), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                cursor
                    .getString(0)
                    ?.lines()
                    ?.map(String::trim)
                    ?.filter(String::isNotEmpty)
                    ?.joinToString(", ")
                    ?.takeIf(String::isNotEmpty)
            }
}
