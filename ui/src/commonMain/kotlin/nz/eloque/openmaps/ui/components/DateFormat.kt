package nz.eloque.openmaps.ui.components

import kotlinx.datetime.LocalDate

/** [date] in the device's own short date format. */
expect fun formatDate(date: LocalDate): String
