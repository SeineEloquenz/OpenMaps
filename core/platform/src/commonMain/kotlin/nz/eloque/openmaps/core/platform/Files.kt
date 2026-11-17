package nz.eloque.openmaps.core.platform

import kotlinx.coroutines.flow.Flow

/** Opens files the user picks and hands files to other apps. */
interface Files {
    /** The text of a file the user picks, null when they pick none or it cannot be read. */
    suspend fun pickText(): String?

    /** Texts of files other apps asked this app to open. */
    val opened: Flow<String>

    fun share(
        name: String,
        mimeType: String,
        text: String,
    )
}
