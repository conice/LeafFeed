package me.ash.reader.domain.model.article

data class ArticleFilterCandidate(
    val articleId: String,
    val accountId: Int,
    val title: String,
    val rawDescription: String,
    val feedId: String,
    val groupId: String,
    val isUnread: Boolean = true,
    val audioUrl: String? = null,
)
