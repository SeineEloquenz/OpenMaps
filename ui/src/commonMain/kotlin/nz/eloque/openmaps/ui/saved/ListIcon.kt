package nz.eloque.openmaps.ui.saved

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons a list can have. The names are stored, so they must not change. */
enum class ListIcon(
    val vector: ImageVector,
) {
    LIST(Icons.AutoMirrored.Filled.List),
    STAR(Icons.Default.Star),
    HEART(Icons.Default.Favorite),
    FLAG(Icons.Default.Flag),
    RESTAURANT(Icons.Default.Restaurant),
    CAFE(Icons.Default.LocalCafe),
    BAR(Icons.Default.LocalBar),
    SHOPPING(Icons.Default.ShoppingBag),
    HOTEL(Icons.Default.Hotel),
    TRAVEL(Icons.Default.Flight),
    BEACH(Icons.Default.BeachAccess),
    HIKING(Icons.Default.Hiking),
    PARK(Icons.Default.Park),
    MUSEUM(Icons.Default.Museum),
    SIGHTS(Icons.Default.PhotoCamera),
    CAR(Icons.Default.DirectionsCar),
    HOME(Icons.Default.Home),
    WORK(Icons.Default.Work),
    SCHOOL(Icons.Default.School),
    PETS(Icons.Default.Pets),
    ;

    companion object {
        fun of(name: String?): ListIcon = entries.firstOrNull { it.name == name } ?: LIST
    }
}
