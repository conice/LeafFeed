package com.conice.morss.infrastructure.ai

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "ai_connection",
)
data class AiConnectionEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val authType: String,
    val secretRef: String?,
    val enabled: Boolean,
    val revision: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "ai_model_profile",
    foreignKeys = [
        ForeignKey(
            entity = AiConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["connectionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("connectionId"),
        Index(value = ["connectionId", "modelId"], unique = true),
    ],
)
data class AiModelProfileEntity(
    @androidx.room.PrimaryKey val id: String,
    val connectionId: String,
    val modelId: String,
    val displayName: String,
    val maxOutputTokens: Int,
    val temperature: Double?,
    val enabled: Boolean,
    val revision: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "ai_prompt", indices = [Index("task")])
data class AiPromptEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val task: String,
    val systemTemplate: String,
    val userTemplate: String,
    val itemTemplate: String,
    val outputMode: String,
    val builtIn: Boolean,
    val revision: Long,
    val updatedAt: Long,
)

@Entity(tableName = "ai_task_binding")
data class AiTaskBindingEntity(
    @androidx.room.PrimaryKey val task: String,
    val promptId: String,
    val primaryModelId: String,
    val fallbackModelIdsJson: String,
    val articleCount: Int,
    val updatedAt: Long,
)

@Entity(tableName = "ai_secret")
data class AiSecretEntity(
    @androidx.room.PrimaryKey val id: String,
    val ciphertext: String,
    val updatedAt: Long,
)

@Dao
interface AiDao {
    @Query("SELECT * FROM ai_connection ORDER BY updatedAt DESC")
    fun observeConnections(): Flow<List<AiConnectionEntity>>

    @Query("SELECT * FROM ai_connection ORDER BY updatedAt DESC")
    suspend fun queryConnections(): List<AiConnectionEntity>

    @Query("SELECT * FROM ai_connection WHERE id = :id LIMIT 1")
    suspend fun queryConnection(id: String): AiConnectionEntity?

    @Upsert
    suspend fun upsertConnection(connection: AiConnectionEntity)

    @Query("DELETE FROM ai_connection WHERE id = :id")
    suspend fun deleteConnection(id: String)

    @Query("SELECT * FROM ai_model_profile WHERE connectionId = :connectionId ORDER BY updatedAt DESC")
    fun observeModels(connectionId: String): Flow<List<AiModelProfileEntity>>

    @Query("SELECT * FROM ai_model_profile WHERE connectionId = :connectionId ORDER BY updatedAt DESC")
    suspend fun queryModels(connectionId: String): List<AiModelProfileEntity>

    @Query("SELECT * FROM ai_model_profile ORDER BY updatedAt DESC")
    suspend fun queryModels(): List<AiModelProfileEntity>

    @Query("SELECT * FROM ai_model_profile ORDER BY updatedAt DESC")
    fun observeAllModels(): Flow<List<AiModelProfileEntity>>

    @Query("SELECT * FROM ai_model_profile WHERE id = :id LIMIT 1")
    suspend fun queryModel(id: String): AiModelProfileEntity?

    @Upsert
    suspend fun upsertModel(model: AiModelProfileEntity)

    @Query("DELETE FROM ai_model_profile WHERE id = :id")
    suspend fun deleteModel(id: String)

    @Query("SELECT * FROM ai_prompt WHERE (:task IS NULL OR task = :task) ORDER BY builtIn DESC, updatedAt DESC")
    fun observePrompts(task: String?): Flow<List<AiPromptEntity>>

    @Query("SELECT * FROM ai_prompt WHERE (:task IS NULL OR task = :task) ORDER BY builtIn DESC, updatedAt DESC")
    suspend fun queryPrompts(task: String?): List<AiPromptEntity>

    @Query("SELECT * FROM ai_prompt WHERE id = :id LIMIT 1")
    suspend fun queryPrompt(id: String): AiPromptEntity?

    @Upsert
    suspend fun upsertPrompt(prompt: AiPromptEntity)

    @Query("DELETE FROM ai_prompt WHERE id = :id")
    suspend fun deletePrompt(id: String)

    @Query("SELECT * FROM ai_task_binding WHERE task = :task LIMIT 1")
    suspend fun queryBinding(task: String): AiTaskBindingEntity?

    @Query("SELECT * FROM ai_task_binding WHERE task = :task LIMIT 1")
    fun observeBinding(task: String): Flow<AiTaskBindingEntity?>

    @Query("SELECT * FROM ai_task_binding ORDER BY task")
    suspend fun queryBindings(): List<AiTaskBindingEntity>

    @Upsert
    suspend fun upsertBinding(binding: AiTaskBindingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecret(secret: AiSecretEntity)

    @Query("SELECT * FROM ai_secret WHERE id = :id LIMIT 1")
    suspend fun querySecret(id: String): AiSecretEntity?

    @Query("DELETE FROM ai_secret WHERE id = :id")
    suspend fun deleteSecret(id: String)
}

@Database(
    entities = [
        AiConnectionEntity::class,
        AiModelProfileEntity::class,
        AiPromptEntity::class,
        AiTaskBindingEntity::class,
        AiSecretEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AiDatabase : RoomDatabase() {
    abstract fun aiDao(): AiDao

    companion object {
        @Volatile private var instance: AiDatabase? = null

        fun getInstance(context: Context): AiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AiDatabase::class.java,
                    "MorssAi",
                ).build().also { instance = it }
            }
    }
}
