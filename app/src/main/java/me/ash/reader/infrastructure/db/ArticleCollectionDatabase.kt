package me.ash.reader.infrastructure.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import me.ash.reader.domain.model.article.ArticleNote
import me.ash.reader.domain.model.article.ArticleTagCrossRef
import me.ash.reader.domain.model.article.ArticleTagLabel
import me.ash.reader.domain.model.article.SavedSearch
import me.ash.reader.domain.repository.ArticleCollectionDao

@Database(
    entities = [
        ArticleTagLabel::class,
        ArticleTagCrossRef::class,
        ArticleNote::class,
        SavedSearch::class,
    ],
    version = 1,
)
abstract class ArticleCollectionDatabase : RoomDatabase() {
    abstract fun articleCollectionDao(): ArticleCollectionDao

    companion object {
        @Volatile private var instance: ArticleCollectionDatabase? = null

        fun getInstance(context: Context): ArticleCollectionDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArticleCollectionDatabase::class.java,
                    "ReaderCollections",
                ).build().also { instance = it }
            }
    }
}
