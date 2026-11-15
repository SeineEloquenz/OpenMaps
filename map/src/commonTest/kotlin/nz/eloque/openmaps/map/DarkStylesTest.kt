package nz.eloque.openmaps.map

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.maplibre.compose.style.BaseStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DarkStylesTest {
    @Test
    fun fallsBackAndRetriesUntilTheLightStyleLoads() =
        runTest {
            var attempts = 0
            val styles =
                darkStyles {
                    attempts++
                    if (attempts < 4) error("offline")
                    """{"version":8,"layers":[]}"""
                }.toList()

            assertEquals(2, styles.size)
            assertIs<BaseStyle.Uri>(styles[0])
            assertIs<BaseStyle.Json>(styles[1])
            assertEquals(4, attempts)
            assertEquals(5_000L + 10_000L + 20_000L, currentTime)
        }

    @Test
    fun loadsDirectlyWhenOnline() =
        runTest {
            val styles = darkStyles { """{"version":8,"layers":[]}""" }.toList()

            assertIs<BaseStyle.Json>(styles.single())
            assertEquals(0L, currentTime)
        }
}
