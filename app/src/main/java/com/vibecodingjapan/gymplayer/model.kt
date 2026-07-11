package com.vibecodingjapan.gymplayer

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.roundToInt

data class UserSession(val uid: String = "", val email: String = "", val loggedIn: Boolean = false, val lastSyncAt: Long = 0)

data class Machine(
  val id: String,
  val number: String,
  val name: String,
  val bodyPart: String,
  val icon: String,
  val targetSets: Int = 3,
  val defaultWeight: Int = 40,
  val imageStorageUrl: String? = null,
  val localImagePath: String? = null,
  val updatedAt: Long = 0,
)

data class WorkoutSet(
  val id: String = UUID.randomUUID().toString(),
  val sessionId: String,
  val machineId: String,
  val machineNumber: String,
  val machineName: String,
  val setIndex: Int,
  val weightKg: Int,
  val reps: Int,
  val completedAt: LocalDateTime = LocalDateTime.now(),
)

data class WorkoutSession(
  val id: String = UUID.randomUUID().toString(),
  val uid: String,
  val date: LocalDate = LocalDate.now(),
  val startedAt: LocalDateTime = LocalDateTime.now(),
  val endedAt: LocalDateTime? = null,
  val systolic: Int = 120,
  val diastolic: Int = 80,
  val pulse: Int = 72,
  val bodyFatPercent: Double? = null,
  val muscleMassKg: Double? = null,
  val bodyWaterPercent: Double? = null,
  val weightKg: Double? = null,
  val bmi: Double? = null,
  val basalMetabolism: Double? = null,
  val visceralFat: Double? = null,
  val synced: Boolean = false,
)

data class ActiveWorkoutDraft(
  val selectedMachineIds: List<String> = emptyList(),
  val selectedMachineId: String = "",
  val bodyCheck: BodyCheck = BodyCheck(),
  val restDurationSeconds: Int = 50,
)

data class Playlist(val id: String = UUID.randomUUID().toString(), val name: String, val folderUri: String = "", val createdAt: Long = System.currentTimeMillis())

data class Track(
  val id: String = UUID.randomUUID().toString(),
  val playlistId: String,
  val uri: String,
  val title: String,
  val artist: String = "",
  val durationMs: Long = 0,
  val orderIndex: Int = 0,
)

data class BodyCheck(val systolic: Int = 120, val diastolic: Int = 80, val pulse: Int = 72)

data class BodyResult(
  val weightKg: String = "",
  val bodyFatPercent: String = "",
  val muscleMassKg: String = "",
  val bodyWaterPercent: String = "",
  val bmi: String = "",
  val basalMetabolism: String = "",
  val visceralFat: String = "",
)

enum class Screen { Home, Music, TrainingMenu, BodyCheck, Training, FinishBody, History, HistoryEdit, Settings }

enum class SetStatus { Pending, Active, Resting, Complete }

enum class PlaybackMode { PlaylistLoop, SingleLoop, Shuffle }

enum class WeightUnit(val label: String) { LB("lb"), KG("kg") }

data class SyncDialogState(
  val visible: Boolean = false,
  val processing: Boolean = false,
  val title: String = "",
  val message: String = "",
  val progress: Float = 0f,
  val tasks: List<String> = emptyList(),
  val currentTask: String = "",
)

data class SyncProgress(val progress: Float, val currentTask: String, val tasks: List<String>)

private const val LB_PER_KG = 2.2046226218

fun displayWeightFromLb(weightLb: Int, unit: WeightUnit): Int =
  when (unit) {
    WeightUnit.LB -> weightLb
    WeightUnit.KG -> (weightLb / LB_PER_KG).roundToInt()
  }

fun displayWeightFromLb(weightLb: Double?, unit: WeightUnit): String =
  weightLb?.let {
    when (unit) {
      WeightUnit.LB -> "%.1f lb".format(it)
      WeightUnit.KG -> "%.1f kg".format(it / LB_PER_KG)
    }
  } ?: "-"

fun displayWeightToLb(value: Int, unit: WeightUnit): Int =
  when (unit) {
    WeightUnit.LB -> value
    WeightUnit.KG -> (value * LB_PER_KG).roundToInt()
  }

fun displayWeightToLb(value: Double?, unit: WeightUnit): Double? =
  value?.let {
    when (unit) {
      WeightUnit.LB -> it
      WeightUnit.KG -> it * LB_PER_KG
    }
  }

fun defaultWeightLbForMachine(machine: Machine, sessions: List<WorkoutSession>, sets: List<WorkoutSet>): Int {
  val recentSessionId =
    sessions
      .sortedByDescending { it.startedAt }
      .firstOrNull { session -> sets.any { it.sessionId == session.id && it.machineId == machine.id } }
      ?.id
      ?: return machine.defaultWeight
  return sets
    .filter { it.sessionId == recentSessionId && it.machineId == machine.id }
    .maxOfOrNull { it.weightKg }
    ?: machine.defaultWeight
}

data class TrainingMachineState(
  val machine: Machine,
  val completedSets: List<WorkoutSet> = emptyList(),
  val activeSet: Int = 1,
  val weightKg: Int = machine.defaultWeight,
  val reps: Int = 10,
  val status: SetStatus = SetStatus.Active,
) {
  val done: Boolean get() = completedSets.size >= machine.targetSets
}
