package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun changedOnlyWhenBothAreKnownAndDiffer() {
        assertTrue(platformChanged("5", "3"))
        assertFalse(platformChanged("3", "3"))
        assertFalse(platformChanged("5", null))
        assertFalse(platformChanged(null, "3"))
    }

    @Test
    fun sameNumberWrittenDifferentlyIsNoChange() {
        assertFalse(platformChanged("2", "2(U)"))
        assertFalse(platformChanged("2a", "2"))
        assertFalse(platformChanged("02", "2"))
        assertTrue(platformChanged("12", "1"))
        assertTrue(platformChanged("3 (U)", "2(U)"))
    }

    @Test
    fun lettersCompareWithoutCaseOrBrackets() {
        assertFalse(platformChanged("b", "B"))
        assertFalse(platformChanged("B (Bus)", "B"))
        assertTrue(platformChanged("B2", "B3"))
        assertTrue(platformChanged("C", "B"))
    }
}
