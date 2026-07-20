package com.vibecodingjapan.gymplayer

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user_session")
data class UserSessionEntity(@PrimaryKey val uid: String, val email: String, val loggedIn: Boolean, val lastSyncAt: Long)

@Entity(tableName = "machines")
data class MachineEntity(
  @PrimaryKey val id: String,
  val number: String,
  val name: String,
  val bodyPart: String,
  val icon: String,
  val targetSets: Int,
  val defaultWeight: Int,
  val imageStorageUrl: String?,
  val localImagePath: String?,
  val updatedAt: Long,
  val nameZh: String,
  val nameEn: String,
  val nameKo: String,
  val bodyPartZh: String,
  val bodyPartEn: String,
  val bodyPartKo: String,
)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
  @PrimaryKey val id: String,
  val uid: String,
  val date: String,
  val startedAt: String,
  val endedAt: String?,
  val systolic: Int,
  val diastolic: Int,
  val pulse: Int,
  val bodyFatPercent: Double?,
  val muscleMassKg: Double?,
  val bodyWaterPercent: Double?,
  val weightKg: Double?,
  val bmi: Double?,
  val basalMetabolism: Double?,
  val visceralFat: Double?,
  val synced: Boolean,
)

@Entity(tableName = "workout_sets")
data class WorkoutSetEntity(
  @PrimaryKey val id: String,
  val sessionId: String,
  val machineId: String,
  val machineNumber: String,
  val machineName: String,
  val setIndex: Int,
  val weightKg: Int,
  val reps: Int,
  val completedAt: String,
)

@Entity(tableName = "active_workout_drafts")
data class ActiveWorkoutDraftEntity(
  @PrimaryKey val id: String,
  val selectedMachineIds: String,
  val selectedMachineId: String,
  val systolic: Int,
  val diastolic: Int,
  val pulse: Int,
  val restDurationSeconds: Int,
  val updatedAt: Long,
)

@Entity(tableName = "deleted_workout_sessions")
data class DeletedWorkoutSessionEntity(@PrimaryKey val id: String, val uid: String, val deletedAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey val id: String, val name: String, val folderUri: String, val createdAt: Long)

@Entity(tableName = "tracks")
data class TrackEntity(@PrimaryKey val id: String, val playlistId: String, val uri: String, val title: String, val artist: String, val durationMs: Long, val orderIndex: Int)

@Dao
interface GymPlayerDao {
  @Query("SELECT * FROM machines ORDER BY CAST(number AS INTEGER), number")
  fun machines(): Flow<List<MachineEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertMachines(machines: List<MachineEntity>)

  @Query("DELETE FROM machines")
  suspend fun clearMachines()

  @Query("SELECT * FROM workout_sessions ORDER BY date DESC")
  fun workoutSessions(): Flow<List<WorkoutSessionEntity>>

  @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY completedAt")
  fun workoutSets(sessionId: String): Flow<List<WorkoutSetEntity>>

  @Query("SELECT * FROM workout_sets ORDER BY completedAt")
  fun allWorkoutSets(): Flow<List<WorkoutSetEntity>>

  @Query("SELECT * FROM workout_sessions WHERE synced = 0")
  suspend fun unsyncedSessions(): List<WorkoutSessionEntity>

  @Query("SELECT * FROM workout_sessions WHERE id = :sessionId LIMIT 1")
  suspend fun workoutSessionById(sessionId: String): WorkoutSessionEntity?

  @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY setIndex")
  suspend fun setsForSession(sessionId: String): List<WorkoutSetEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSession(session: WorkoutSessionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSets(sets: List<WorkoutSetEntity>)

  @Query("DELETE FROM workout_sets WHERE id = :setId")
  suspend fun deleteWorkoutSet(setId: String)

  @Query("DELETE FROM workout_sets WHERE sessionId = :sessionId")
  suspend fun deleteWorkoutSetsForSession(sessionId: String)

  @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
  suspend fun deleteWorkoutSession(sessionId: String)

  @Query("SELECT * FROM active_workout_drafts WHERE id = :id LIMIT 1")
  fun activeWorkoutDraft(id: String): Flow<ActiveWorkoutDraftEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertActiveWorkoutDraft(draft: ActiveWorkoutDraftEntity)

  @Query("DELETE FROM active_workout_drafts WHERE id = :id")
  suspend fun deleteActiveWorkoutDraft(id: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDeletedWorkoutSession(session: DeletedWorkoutSessionEntity)

  @Query("SELECT * FROM deleted_workout_sessions ORDER BY deletedAt")
  suspend fun deletedWorkoutSessions(): List<DeletedWorkoutSessionEntity>

  @Query("DELETE FROM deleted_workout_sessions WHERE id = :sessionId")
  suspend fun clearDeletedWorkoutSession(sessionId: String)

  @Query("UPDATE workout_sessions SET synced = 1 WHERE id = :sessionId")
  suspend fun markSessionSynced(sessionId: String)

  @Query("SELECT * FROM playlists ORDER BY createdAt")
  fun playlists(): Flow<List<PlaylistEntity>>

  @Query("SELECT * FROM tracks WHERE playlistId = :playlistId ORDER BY orderIndex")
  fun tracks(playlistId: String): Flow<List<TrackEntity>>

  @Query("SELECT * FROM tracks ORDER BY playlistId, orderIndex")
  fun allTracks(): Flow<List<TrackEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPlaylist(playlist: PlaylistEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTracks(tracks: List<TrackEntity>)

  @Query("DELETE FROM playlists WHERE id = :playlistId")
  suspend fun deletePlaylist(playlistId: String)

  @Query("DELETE FROM tracks WHERE playlistId = :playlistId")
  suspend fun deleteTracksForPlaylist(playlistId: String)
}

@Database(
  entities = [UserSessionEntity::class, MachineEntity::class, WorkoutSessionEntity::class, WorkoutSetEntity::class, ActiveWorkoutDraftEntity::class, DeletedWorkoutSessionEntity::class, PlaylistEntity::class, TrackEntity::class],
  version = 7,
  exportSchema = false,
)
abstract class GymPlayerDatabase : RoomDatabase() {
  abstract fun dao(): GymPlayerDao
}

val MIGRATION_1_2 =
  object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE machines ADD COLUMN imageStorageUrl TEXT")
      db.execSQL("ALTER TABLE machines ADD COLUMN localImagePath TEXT")
    }
  }

val MIGRATION_2_3 =
  object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("CREATE TABLE IF NOT EXISTS deleted_workout_sessions (id TEXT NOT NULL PRIMARY KEY, uid TEXT NOT NULL, deletedAt INTEGER NOT NULL)")
    }
  }

val MIGRATION_3_4 =
  object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE workout_sessions ADD COLUMN bodyFatPercent REAL")
      db.execSQL("ALTER TABLE workout_sessions ADD COLUMN bmi REAL")
      db.execSQL("ALTER TABLE workout_sessions ADD COLUMN basalMetabolism REAL")
      db.execSQL("ALTER TABLE workout_sessions ADD COLUMN visceralFat REAL")
    }
  }

val MIGRATION_4_5 =
  object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE machines_new (
          id TEXT NOT NULL PRIMARY KEY,
          number TEXT NOT NULL,
          name TEXT NOT NULL,
          bodyPart TEXT NOT NULL,
          icon TEXT NOT NULL,
          targetSets INTEGER NOT NULL,
          defaultWeight INTEGER NOT NULL,
          imageStorageUrl TEXT,
          localImagePath TEXT,
          updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
      )
      db.execSQL(
        """
        INSERT INTO machines_new (id, number, name, bodyPart, icon, targetSets, defaultWeight, imageStorageUrl, localImagePath, updatedAt)
        SELECT id, CAST(number AS TEXT), name, bodyPart, icon, targetSets, defaultWeight, imageStorageUrl, localImagePath, updatedAt
        FROM machines
        """.trimIndent(),
      )
      db.execSQL("DROP TABLE machines")
      db.execSQL("ALTER TABLE machines_new RENAME TO machines")

      db.execSQL(
        """
        CREATE TABLE workout_sets_new (
          id TEXT NOT NULL PRIMARY KEY,
          sessionId TEXT NOT NULL,
          machineId TEXT NOT NULL,
          machineNumber TEXT NOT NULL,
          machineName TEXT NOT NULL,
          setIndex INTEGER NOT NULL,
          weightKg INTEGER NOT NULL,
          reps INTEGER NOT NULL,
          completedAt TEXT NOT NULL
        )
        """.trimIndent(),
      )
      db.execSQL(
        """
        INSERT INTO workout_sets_new (id, sessionId, machineId, machineNumber, machineName, setIndex, weightKg, reps, completedAt)
        SELECT id, sessionId, machineId, CAST(machineNumber AS TEXT), machineName, setIndex, weightKg, reps, completedAt
        FROM workout_sets
        """.trimIndent(),
      )
      db.execSQL("DROP TABLE workout_sets")
      db.execSQL("ALTER TABLE workout_sets_new RENAME TO workout_sets")
    }
  }

val MIGRATION_5_6 =
  object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS active_workout_drafts (
          id TEXT NOT NULL PRIMARY KEY,
          selectedMachineIds TEXT NOT NULL,
          selectedMachineId TEXT NOT NULL,
          systolic INTEGER NOT NULL,
          diastolic INTEGER NOT NULL,
          pulse INTEGER NOT NULL,
          restDurationSeconds INTEGER NOT NULL,
          updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
      )
    }
  }

val MIGRATION_6_7 =
  object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE machines ADD COLUMN nameZh TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE machines ADD COLUMN nameEn TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE machines ADD COLUMN nameKo TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE machines ADD COLUMN bodyPartZh TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE machines ADD COLUMN bodyPartEn TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE machines ADD COLUMN bodyPartKo TEXT NOT NULL DEFAULT ''")
    }
  }
