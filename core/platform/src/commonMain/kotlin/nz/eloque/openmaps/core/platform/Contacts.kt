package nz.eloque.openmaps.core.platform

/** Lets the user pick an address from their contacts without granting access to all of them. */
interface Contacts {
    /** Null when the user picks none or the device has no contacts app. */
    suspend fun pickAddress(): String?
}
