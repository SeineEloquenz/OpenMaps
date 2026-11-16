package nz.eloque.openmaps.ui.components

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

actual fun formatDate(date: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date.toJavaLocalDate())
