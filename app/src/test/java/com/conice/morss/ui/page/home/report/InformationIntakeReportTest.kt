package com.conice.morss.ui.page.home.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InformationIntakeReportTest {
    @Test
    fun `trend compares the current period with the previous period`() {
        assertEquals(
            IntakeTrend(IntakeTrendDirection.UP, 50),
            calculateIntakeTrend(current = 120, previous = 80),
        )
        assertEquals(
            IntakeTrend(IntakeTrendDirection.DOWN, 25),
            calculateIntakeTrend(current = 60, previous = 80),
        )
        assertEquals(
            IntakeTrend(IntakeTrendDirection.NEW, null),
            calculateIntakeTrend(current = 12, previous = 0),
        )
    }

    @Test
    fun `rising intake with steady attention is called out first`() {
        val summary =
            classifyIntakeSummary(
                metrics =
                    IntakeMetrics(
                        received = 120,
                        previousReceived = 80,
                        opened = 20,
                        previousOpened = 20,
                        pending = 70,
                    ),
                groups = emptyList(),
            )

        assertEquals(IntakeSummaryKind.INTAKE_RISING, summary.kind)
    }

    @Test
    fun `a mostly unread new cohort is described without claiming net backlog growth`() {
        val summary =
            classifyIntakeSummary(
                metrics = IntakeMetrics(received = 20, pending = 12),
                groups = emptyList(),
            )

        assertEquals(IntakeSummaryKind.MOST_NEW_ITEMS_WAITING, summary.kind)
    }

    @Test
    fun `attention concentration uses group open share`() {
        val summary =
            classifyIntakeSummary(
                metrics = IntakeMetrics(received = 20, opened = 10, pending = 2),
                groups =
                    listOf(
                        group(name = "Design", incomingShare = .35f, openedShare = .7f),
                        group(name = "News", incomingShare = .65f, openedShare = .3f),
                    ),
            )

        assertEquals(IntakeSummaryKind.ATTENTION_CONCENTRATED, summary.kind)
        assertEquals("Design", summary.focusGroupName)
    }

    @Test
    fun `recommendation selects the largest actionable pressure source`() {
        val leading = source(name = "Daily News", received = 40, pending = 32)
        val recommendation =
            buildIntakeRecommendation(
                pressureSources =
                    listOf(
                        source(name = "Weekly Notes", received = 10, pending = 7),
                        leading,
                    ),
                groups = emptyList(),
            )

        assertTrue(recommendation is IntakeRecommendation.ReviewSource)
        assertEquals(
            leading,
            (recommendation as IntakeRecommendation.ReviewSource).source,
        )
    }

    @Test
    fun `group imbalance is suggested when no source dominates`() {
        val news = group(name = "News", incomingShare = .7f, openedShare = .2f)
        val recommendation =
            buildIntakeRecommendation(
                pressureSources = emptyList(),
                groups = listOf(news),
            )

        assertTrue(recommendation is IntakeRecommendation.ReviewGroup)
        assertEquals(news, (recommendation as IntakeRecommendation.ReviewGroup).group)
    }

    private fun source(
        name: String,
        received: Int,
        pending: Int,
    ) =
        IntakeSourceRow(
            id = name,
            name = name,
            groupId = "group",
            groupName = "Group",
            received = received,
            previousReceived = 0,
            opened = 0,
            previousOpened = 0,
            clearedWithoutOpening = 0,
            saved = 0,
            pending = pending,
            unreadBacklog = pending,
        )

    private fun group(
        name: String,
        incomingShare: Float,
        openedShare: Float,
    ) =
        IntakeGroupRow(
            id = name,
            name = name,
            received = 20,
            opened = 10,
            pending = 0,
            incomingShare = incomingShare,
            openedShare = openedShare,
        )
}
