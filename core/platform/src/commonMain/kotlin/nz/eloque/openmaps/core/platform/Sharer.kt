package nz.eloque.openmaps.core.platform

/** Hands text to the system share sheet. */
fun interface Sharer {
    fun share(text: String)
}
