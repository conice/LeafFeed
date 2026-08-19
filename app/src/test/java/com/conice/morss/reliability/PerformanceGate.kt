package com.conice.morss.reliability

data class PerformanceSample(val medianMillis: Double, val p95Millis: Double)

data class PerformanceGateResult(
    val passed: Boolean,
    val medianRegression: Double,
    val p95Regression: Double,
)

private const val MAX_MEDIAN_REGRESSION = 0.05
private const val MAX_P95_REGRESSION = 0.10
private const val FLOATING_POINT_TOLERANCE = 1e-9

fun evaluatePerformanceGate(
    baseline: PerformanceSample,
    candidate: PerformanceSample,
): PerformanceGateResult {
    require(baseline.medianMillis > 0.0 && baseline.p95Millis > 0.0)
    require(candidate.medianMillis >= 0.0 && candidate.p95Millis >= 0.0)
    val medianRegression = candidate.medianMillis / baseline.medianMillis - 1.0
    val p95Regression = candidate.p95Millis / baseline.p95Millis - 1.0
    return PerformanceGateResult(
        passed =
            medianRegression <= MAX_MEDIAN_REGRESSION + FLOATING_POINT_TOLERANCE &&
                p95Regression <= MAX_P95_REGRESSION + FLOATING_POINT_TOLERANCE,
        medianRegression = medianRegression,
        p95Regression = p95Regression,
    )
}
