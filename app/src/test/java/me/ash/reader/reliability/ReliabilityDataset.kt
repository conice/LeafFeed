package me.ash.reader.reliability

/** Fixed input sizes shared by performance and recovery tests. */
enum class ReliabilityDataset(
    val articleCount: Int,
    val feedCount: Int,
    val podcastCount: Int,
    val ruleCount: Int,
) {
    SMALL(articleCount = 100, feedCount = 10, podcastCount = 0, ruleCount = 0),
    MEDIUM(articleCount = 10_000, feedCount = 100, podcastCount = 100, ruleCount = 10),
    LARGE(articleCount = 50_000, feedCount = 300, podcastCount = 100, ruleCount = 20),
}

data class ReliabilityFixtureId(
    val accountId: Int,
    val feedIndex: Int,
    val articleIndex: Int,
) {
    val feedId: String get() = "fixture-feed-$feedIndex"
    val articleId: String get() = "fixture-article-$articleIndex"
}

fun ReliabilityDataset.articleId(index: Int): String = "fixture-$name-article-$index"
fun ReliabilityDataset.feedId(index: Int): String = "fixture-$name-feed-$index"

data class FixtureArticle(
    val id: String,
    val feedId: String,
    val publishedAtMillis: Long,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val audioUrl: String?,
)

fun ReliabilityDataset.articles(): Sequence<FixtureArticle> =
    (0 until articleCount).asSequence().map { index ->
        val podcast = index < podcastCount
        FixtureArticle(
            id = articleId(index),
            feedId = feedId(index % feedCount),
            publishedAtMillis = FIXTURE_EPOCH_MILLIS + index * 60_000L,
            title = "Fixture article $index",
            description = "Deterministic body for fixture article $index",
            imageUrl = if (index % 3 == 0) "https://fixture.invalid/image/$index.jpg" else null,
            audioUrl = if (podcast) "https://fixture.invalid/audio/$index.mp3" else null,
        )
    }

private const val FIXTURE_EPOCH_MILLIS = 1_700_000_000_000L
