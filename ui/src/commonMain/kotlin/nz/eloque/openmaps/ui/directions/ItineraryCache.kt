package nz.eloque.openmaps.ui.directions

import nz.eloque.openmaps.core.model.Itinerary

/** Holds planned itineraries so the route screen can open one by id. Lost on process death. */
class ItineraryCache {
    private val itineraries = mutableMapOf<String, Itinerary>()

    fun put(values: List<Itinerary>) {
        values.forEach { itineraries[it.id] = it }
    }

    operator fun get(id: String): Itinerary? = itineraries[id]
}
