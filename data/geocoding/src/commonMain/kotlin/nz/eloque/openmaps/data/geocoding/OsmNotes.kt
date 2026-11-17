package nz.eloque.openmaps.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.MapNotes

@Serializable
internal data class OsmNoteDto(
    val properties: OsmNotePropertiesDto,
)

@Serializable
internal data class OsmNotePropertiesDto(
    val id: Long,
)

/** Anonymous OpenStreetMap notes, which need no account. */
internal class OsmNotes(
    private val client: HttpClient,
    private val baseUrl: String = OSM_API,
) : MapNotes {
    override suspend fun report(
        location: LatLng,
        text: String,
    ): String {
        val note: OsmNoteDto =
            client
                .submitForm(
                    "$baseUrl/api/0.6/notes.json",
                    parameters {
                        append("lat", location.latitude.toString())
                        append("lon", location.longitude.toString())
                        append("text", "$text\n\nvia OpenMaps")
                    },
                ).body()
        return "https://www.openstreetmap.org/note/${note.properties.id}"
    }
}

internal const val OSM_API = "https://api.openstreetmap.org"
