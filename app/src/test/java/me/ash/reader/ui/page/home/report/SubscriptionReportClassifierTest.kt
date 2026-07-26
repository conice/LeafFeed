package me.ash.reader.ui.page.home.report

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionReportClassifierTest {
    private val today = LocalDate.of(2026, 7, 26)

    @Test
    fun `new subscriptions are not labelled inactive or rarely opened`() {
        val issues =
            classifySubscription(
                ReportClassificationInput(
                    isNew = true,
                    latestDate = today.minusDays(2),
                    articleCount = 10,
                    today = today,
                )
            )

        assertTrue(ReportIssue.NEW_SUBSCRIPTION in issues)
        assertFalse(ReportIssue.POSSIBLY_INACTIVE in issues)
        assertFalse(ReportIssue.NEVER_OPENED in issues)
    }

    @Test
    fun `subscription without articles is not assumed inactive`() {
        val issues = classifySubscription(ReportClassificationInput(today = today))

        assertTrue(ReportIssue.NO_ARTICLES_YET in issues)
        assertFalse(ReportIssue.POSSIBLY_INACTIVE in issues)
    }

    @Test
    fun `high unread low engagement feed receives actionable labels`() {
        val issues =
            classifySubscription(
                ReportClassificationInput(
                    latestDate = today,
                    articleCount = 90,
                    engagedCount = 2,
                    periodUnread = 85,
                    unreadBacklog = 300,
                    today = today,
                )
            )

        assertTrue(ReportIssue.HIGH_VOLUME in issues)
        assertTrue(ReportIssue.UNREAD_BUILDUP in issues)
        assertFalse(ReportIssue.HEALTHY in issues)
    }

    @Test
    fun `inactive label respects the feeds expected update gap`() {
        val recentCadence =
            classifySubscription(
                ReportClassificationInput(
                    latestDate = today.minusDays(31),
                    staleAfterDays = 14,
                    today = today,
                )
            )
        val slowCadence =
            classifySubscription(
                ReportClassificationInput(
                    latestDate = today.minusDays(31),
                    staleAfterDays = 60,
                    today = today,
                )
            )

        assertTrue(ReportIssue.POSSIBLY_INACTIVE in recentCadence)
        assertFalse(ReportIssue.POSSIBLY_INACTIVE in slowCadence)
    }

    @Test
    fun `frequently opened feed is healthy`() {
        val issues =
            classifySubscription(
                ReportClassificationInput(
                    latestDate = today,
                    articleCount = 20,
                    engagedCount = 15,
                    wasEverOpened = true,
                    today = today,
                )
            )

        assertTrue(ReportIssue.FREQUENTLY_OPENED in issues)
        assertTrue(ReportIssue.HEALTHY in issues)
    }
}
