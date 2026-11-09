package io.github.romanvht.byedpi

import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.utility.AutoTuneUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AutoTuneUtilsTest {

    private fun strategy(
        command: String,
        success: Int,
        total: Int,
        completed: Boolean = true,
    ) = StrategyResult(
        command = command,
        successCount = success,
        totalRequests = total,
        isCompleted = completed,
    )

    @Test
    fun sampleSites_keepsShortListUntouched() {
        val sites = listOf("a.com", "b.com", "c.com")

        assertEquals(sites, AutoTuneUtils.sampleSites(sites, limit = 10))
    }

    @Test
    fun sampleSites_spreadsSitesAcrossTheWholeList() {
        val sites = (1..20).map { "site$it.com" }

        val sample = AutoTuneUtils.sampleSites(sites, limit = 10)

        assertEquals(10, sample.size)
        assertEquals("site1.com", sample.first())
        // Last bucket starts at index 18 (0-based), i.e. 19th site.
        assertEquals("site19.com", sample.last())
        assertEquals(sample.size, sample.distinct().size)
    }

    @Test
    fun pickBest_returnsHighestSuccessRate() {
        val weak = strategy("-o1", success = 4, total = 10)
        val strong = strategy("-o2", success = 9, total = 10)

        assertSame(strong, AutoTuneUtils.pickBest(listOf(weak, strong)))
    }

    @Test
    fun pickBest_prefersEarlierCandidateOnTie() {
        val first = strategy("-o1", success = 8, total = 10)
        val second = strategy("-o2", success = 8, total = 10)

        assertSame(first, AutoTuneUtils.pickBest(listOf(first, second)))
    }

    @Test
    fun pickBest_ignoresIncompleteStrategies() {
        val incomplete = strategy("-o1", success = 10, total = 10, completed = false)
        val completed = strategy("-o2", success = 5, total = 10)

        assertSame(completed, AutoTuneUtils.pickBest(listOf(incomplete, completed)))
    }

    @Test
    fun pickBest_rejectsResultsBelowMinimum() {
        val bad = strategy("-o1", success = 3, total = 10)

        assertNull(AutoTuneUtils.pickBest(listOf(bad)))
    }

    @Test
    fun pickBest_handlesEmptyList() {
        assertNull(AutoTuneUtils.pickBest(emptyList()))
    }
}
