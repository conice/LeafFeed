package me.ash.reader.reliability

data class PerformanceSample(val medianMillis: Double, val p95Millis: Double)

data class PerformanceGateResult(
    val passed: Boolean,
    val medianRegression: Double,
    val p95Regression: Double,
)

fun evaluatePerformanceGate(
    baseline: PerformanceSample,
    candidate: PerformanceSample,
): PerformanceGateResult {
    require(baseline.medianMillis > 0.0 && baseline.p95Millis > 0.0)
    require(candidate.medianMillis >= 0.0 && candidate.p95Millis >= 0.0)
    val medianRegression = candidate.medianMillis / baseline.medianMillis - 1.0
    val p95Regression = candidate.p95Millis / baseline.p95Millis - 1.0
    return PerformanceGateResult(
        passed = medianRegression <= 0.05 && p95Regression <= 0.10,
        medianRegression = medianRegression,
        p95Regression = p95Regression,
    )
}
