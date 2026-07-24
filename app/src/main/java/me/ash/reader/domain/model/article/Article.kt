package me.ash.reader.domain.model.article

import androidx.room.*
import me.ash.reader.domain.model.feed.Feed
import java.util.*

/**
 * TODO: Add class description
 */
@Entity(
    tableName = "article",
    indices = [
        Index(value = ["accountId", "date"]),
        Index(value = ["accountId", "isUnread", "date"]),
        Index(value = ["accountId", "isStarred", "date"]),
        Index(value = ["accountId", "isUnread", "feedId", "date"]),
        Index(value = ["accountId", "isStarred", "feedId", "date"]),
    ],
    foreignKeys = [ForeignKey(
        entity = Feed::class,
        parentColumns = ["id"],
        childColumns = ["feedId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE
    )]
)
data class Article(
    @PrimaryKey
    var id: String,
    @ColumnInfo
    var date: Date,
    @ColumnInfo
    var title: String,
    @ColumnInfo
    var author: String? = null,
    @ColumnInfo
    var rawDescription: String,
    @ColumnInfo
    var shortDescription: String,
    @ColumnInfo
    @Deprecated("fullContent is the same as rawDescription")
    var fullContent: String? = null,
    @ColumnInfo
    var img: String? = null,
    @ColumnInfo
    var link: String,
    @ColumnInfo(index = true)
    var feedId: String,
    @ColumnInfo(index = true)
    var accountId: Int,
    @ColumnInfo
    var isUnread: Boolean = true,
    @ColumnInfo
    var isStarred: Boolean = false,
    @ColumnInfo
    var isReadLater: Boolean = false,
    @ColumnInfo
    var updateAt: Date? = null,
    /** Podcast episode media metadata. Null for ordinary RSS articles. */
    @ColumnInfo var audioUrl: String? = null,
    @ColumnInfo var audioMimeType: String? = null,
    @ColumnInfo var audioLength: Long? = null,
    @ColumnInfo var durationSeconds: Long? = null,
    @ColumnInfo var episodeGuid: String? = null,
    @ColumnInfo var seasonNumber: Int? = null,
    @ColumnInfo var episodeNumber: Int? = null,
    @ColumnInfo var transcriptUrl: String? = null,
    @ColumnInfo var isExplicit: Boolean = false,
    @ColumnInfo var playbackPositionMs: Long = 0L,
    @ColumnInfo var isPlayed: Boolean = false,
    @ColumnInfo var downloadedPath: String? = null,
) {

    @Ignore
    var dateString: String? = null
}
