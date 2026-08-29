package com.wakechallenge.alarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A single song the user has added to their alarm music pool.
 * `uri` is a persisted content:// URI (permission taken via takePersistableUriPermission
 * when the user picks it), so it stays readable across reboots.
 */
@Entity(tableName = "music_pool")
data class MusicPoolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String
)

@Dao
interface MusicPoolDao {
    @Query("SELECT * FROM music_pool ORDER BY displayName")
    fun observeAll(): Flow<List<MusicPoolEntity>>

    @Query("SELECT * FROM music_pool")
    suspend fun getAllOnce(): List<MusicPoolEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MusicPoolEntity): Long

    @Delete
    suspend fun delete(entry: MusicPoolEntity)
}
