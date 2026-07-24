package me.ash.reader.reliability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReliabilityDatasetTest {
    @Test
    fun `dataset sizes stay fixed`() {
        assertEquals(100, ReliabilityDataset.SMALL.articleCount)
        assertEquals(10_000, ReliabilityDataset.MEDIUM.articleCount)
        assertEquals(50_000, ReliabilityDataset.LARGE.articleCount)
        assertEquals(300, ReliabilityDataset.LARGE.feedCount)
    }

    @Test
    fun `fixture identifiers are deterministic and scoped`() {
        assertEquals("fixture-LARGE-article-42", ReliabilityDataset.LARGE.articleId(42))
        assertNotEquals(
            ReliabilityDataset.SMALL.articleId(42),
            ReliabilityDataset.LARGE.articleId(42),
        )
    }

    @Test
    fun `fixture generation is deterministic`() {
        val first = ReliabilityDataset.MEDIUM.articles().take(101).toList()
        val second = ReliabilityDataset.MEDIUM.articles().take(101).toList()

        assertEquals(first, second)
        assertEquals(100, first.count { it.audioUrl != null })
        assertEquals("fixture-MEDIUM-feed-0", first[100].feedId)
    }

    @Test
    fun `performance gate enforces median and p95 limits`() {
        assertEquals(
            true,
            evaluatePerformanceGate(
                baseline = PerformanceSample(100.0, 200.0),
                candidate = PerformanceSample(105.0, 220.0),
            ).passed,
        )
        assertEquals(
            false,
            evaluatePerformanceGate(
                baseline = PerformanceSample(100.0, 200.0),
                candidate = PerformanceSample(106.0, 200.0),
            ).passed,
        )
    }
}
