package nz.eloque.openmaps.data.reviews

import kotlinx.coroutines.flow.map
import nz.eloque.openmaps.core.model.Reviews
import nz.eloque.openmaps.core.model.ServiceRole
import nz.eloque.openmaps.core.model.ServiceSetting
import nz.eloque.openmaps.core.model.ServiceSettingKind
import nz.eloque.openmaps.core.model.SettingsStore
import org.koin.dsl.module

/** A Mangrove server for reviews of places. */
val ReviewsServer =
    ServiceSetting(
        "server.reviews",
        ServiceRole.REVIEWS,
        ServiceSettingKind.SERVER_URL,
        "Mangrove",
        default = MANGROVE,
        helpUrl = "https://docs.mangrove.reviews",
    )

internal const val MANGROVE = "https://api.mangrove.reviews"

/** The settings this module's sources read. */
val reviewsServiceSettings = listOf(ReviewsServer)

val reviewsModule =
    module {
        single<Reviews> { MangroveReviews(get(), get<SettingsStore>().serviceValue(ReviewsServer.id).map { it ?: MANGROVE }) }
    }
