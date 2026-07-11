package com.vibecodingjapan.gymplayer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.text.Normalizer
import kotlin.random.Random

private val Context.sessionStore by preferencesDataStore("session")
private const val ACTIVE_WORKOUT_DRAFT_ID = "active"

class GymRepository(private val context: Context) {
  private val auth = FirebaseAuth.getInstance()
  private val firestore = FirebaseFirestore.getInstance()
  private val storage = FirebaseStorage.getInstance()
  private val db =
    Room.databaseBuilder(context, GymPlayerDatabase::class.java, "gymplayer.db")
      .fallbackToDestructiveMigration(false)
      .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
      .build()
  private val dao = db.dao()
  private val uidKey = stringPreferencesKey("uid")
  private val emailKey = stringPreferencesKey("email")
  private val loggedInKey = booleanPreferencesKey("logged_in")
  private val lastSyncKey = longPreferencesKey("last_sync")
  private val weightUnitKey = stringPreferencesKey("weight_unit")
  private val restVoiceVolumeKey = floatPreferencesKey("rest_voice_volume")
  private val restAlarmVolumeKey = floatPreferencesKey("rest_alarm_volume")

  val session: Flow<UserSession> =
    context.sessionStore.data.map { prefs ->
      UserSession(prefs[uidKey].orEmpty(), prefs[emailKey].orEmpty(), prefs[loggedInKey] == true, prefs[lastSyncKey] ?: 0)
    }

  val weightUnit: Flow<WeightUnit> =
    context.sessionStore.data.map { prefs ->
      runCatching { WeightUnit.valueOf(prefs[weightUnitKey] ?: WeightUnit.LB.name) }.getOrDefault(WeightUnit.LB)
    }

  val restVoiceVolume: Flow<Float> =
    context.sessionStore.data.map { prefs -> (prefs[restVoiceVolumeKey] ?: 1f).coerceIn(0.5f, 1f) }

  val restAlarmVolume: Flow<Float> =
    context.sessionStore.data.map { prefs -> (prefs[restAlarmVolumeKey] ?: 100f).coerceIn(50f, 100f) }

  val machines: Flow<List<Machine>> =
    dao.machines().map { rows ->
      rows.map { Machine(it.id, it.number, it.name, it.bodyPart, it.icon, it.targetSets, it.defaultWeight, it.imageStorageUrl, it.localImagePath, it.updatedAt) }
    }

  val sessions: Flow<List<WorkoutSession>> =
    dao.workoutSessions().map { rows ->
      rows.map {
        WorkoutSession(
          id = it.id,
          uid = it.uid,
          date = LocalDate.parse(it.date),
          startedAt = LocalDateTime.parse(it.startedAt),
          endedAt = it.endedAt?.let(LocalDateTime::parse),
          systolic = it.systolic,
          diastolic = it.diastolic,
          pulse = it.pulse,
          bodyFatPercent = it.bodyFatPercent,
          muscleMassKg = it.muscleMassKg,
          bodyWaterPercent = it.bodyWaterPercent,
          weightKg = it.weightKg,
          bmi = it.bmi,
          basalMetabolism = it.basalMetabolism,
          visceralFat = it.visceralFat,
          synced = it.synced,
        )
      }
    }

  val savedSets: Flow<List<WorkoutSet>> =
    dao.allWorkoutSets().map { rows ->
      rows.map { WorkoutSet(it.id, it.sessionId, it.machineId, it.machineNumber, it.machineName, it.setIndex, it.weightKg, it.reps, LocalDateTime.parse(it.completedAt)) }
    }

  val activeWorkoutDraft: Flow<ActiveWorkoutDraft?> =
    dao.activeWorkoutDraft(ACTIVE_WORKOUT_DRAFT_ID).map { row ->
      row?.let {
        ActiveWorkoutDraft(
          selectedMachineIds = it.selectedMachineIds.split(",").filter(String::isNotBlank),
          selectedMachineId = it.selectedMachineId,
          bodyCheck = BodyCheck(it.systolic, it.diastolic, it.pulse),
          restDurationSeconds = it.restDurationSeconds,
        )
      }
    }

  val activeWorkoutDraftSets: Flow<List<WorkoutSet>> =
    dao.workoutSets(ACTIVE_WORKOUT_DRAFT_ID).map { rows ->
      rows.map { WorkoutSet(it.id, it.sessionId, it.machineId, it.machineNumber, it.machineName, it.setIndex, it.weightKg, it.reps, LocalDateTime.parse(it.completedAt)) }
    }

  val playlists: Flow<List<Playlist>> =
    dao.playlists().map { rows ->
      rows.map { Playlist(it.id, it.name, it.folderUri, it.createdAt) }
    }

  val allTracks: Flow<List<Track>> =
    dao.allTracks().map { rows ->
      rows.map { Track(it.id, it.playlistId, it.uri, it.title, it.artist, it.durationMs, it.orderIndex) }
    }

  fun tracks(playlistId: String): Flow<List<Track>> =
    dao.tracks(playlistId).map { rows ->
      rows.map { Track(it.id, it.playlistId, it.uri, it.title, it.artist, it.durationMs, it.orderIndex) }
    }

  suspend fun login(email: String, password: String): Result<UserSession> =
    runCatching {
      val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
      val uid = result.user?.uid ?: error("uid is empty")
      context.sessionStore.edit {
        it[uidKey] = uid
        it[emailKey] = email.trim()
        it[loggedInKey] = true
      }
      UserSession(uid, email.trim(), true)
    }

  suspend fun logout() {
    auth.signOut()
    context.sessionStore.edit {
      it[loggedInKey] = false
      it.remove(uidKey)
      it.remove(emailKey)
    }
  }

  suspend fun saveWorkout(session: WorkoutSession, sets: List<WorkoutSet>) {
    dao.upsertSession(session.toEntity())
    dao.upsertSets(sets.map { it.toEntity() })
  }

  suspend fun saveActiveWorkoutDraft(draft: ActiveWorkoutDraft, sets: List<WorkoutSet>) {
    dao.upsertActiveWorkoutDraft(draft.toEntity())
    dao.deleteWorkoutSetsForSession(ACTIVE_WORKOUT_DRAFT_ID)
    dao.upsertSets(sets.map { it.copy(sessionId = ACTIVE_WORKOUT_DRAFT_ID).toEntity() })
  }

  suspend fun clearActiveWorkoutDraft() {
    dao.deleteWorkoutSetsForSession(ACTIVE_WORKOUT_DRAFT_ID)
    dao.deleteActiveWorkoutDraft(ACTIVE_WORKOUT_DRAFT_ID)
  }

  suspend fun deleteWorkoutSet(setId: String) {
    dao.deleteWorkoutSet(setId)
  }

  suspend fun deleteWorkoutSession(sessionId: String) {
    val row = dao.workoutSessionById(sessionId)
    dao.upsertDeletedWorkoutSession(DeletedWorkoutSessionEntity(sessionId, row?.uid.orEmpty(), System.currentTimeMillis()))
    dao.deleteWorkoutSetsForSession(sessionId)
    dao.deleteWorkoutSession(sessionId)
  }

  suspend fun createPlaylist(name: String, folderUri: String, tracks: List<Track>) {
    val playlist = Playlist(name = name.ifBlank { "新しいプレイリスト" }, folderUri = folderUri)
    dao.upsertPlaylist(playlist.toEntity())
    dao.upsertTracks(tracks.mapIndexed { index, track -> track.copy(playlistId = playlist.id, orderIndex = index).toEntity() })
  }

  suspend fun reorderTracks(tracks: List<Track>) {
    dao.upsertTracks(tracks.mapIndexed { index, track -> track.copy(orderIndex = index).toEntity() })
  }

  suspend fun deletePlaylist(playlistId: String) {
    dao.deleteTracksForPlaylist(playlistId)
    dao.deletePlaylist(playlistId)
  }

  suspend fun setWeightUnit(unit: WeightUnit) {
    context.sessionStore.edit { it[weightUnitKey] = unit.name }
  }

  suspend fun setRestVoiceVolume(volume: Float) {
    context.sessionStore.edit { it[restVoiceVolumeKey] = volume.coerceIn(0.5f, 1f) }
  }

  suspend fun setRestAlarmVolume(volume: Float) {
    context.sessionStore.edit { it[restAlarmVolumeKey] = volume.coerceIn(50f, 100f) }
  }

  suspend fun sync(onProgress: (SyncProgress) -> Unit = {}): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val tasks = listOf("通信確認", "ログイン確認", "クラウド削除", "未同期データ送信", "マシン情報取得", "マシン画像取得", "完了")
        fun progress(index: Int, task: String) = onProgress(SyncProgress((index + 1) / tasks.size.toFloat(), task, tasks.take(index + 1)))
        progress(0, "通信確認")
        if (!isOnline()) return@runCatching "オフラインです。ネットワークがありません。"
        progress(1, "ログイン確認")
        val current = session.first()
        require(current.loggedIn && current.uid.isNotBlank()) { "ログインしてください" }
        progress(2, "クラウド削除")
        val deleted = dao.deletedWorkoutSessions()
        for (row in deleted) {
          val uid = row.uid.ifBlank { current.uid }
          firestore.collection("users").document(uid).collection("workoutSessions").document(row.id).delete().await()
          dao.clearDeletedWorkoutSession(row.id)
        }
        progress(3, "未同期データ送信")
        val unsynced = dao.unsyncedSessions()
        for (row in unsynced) {
          val sets = dao.setsForSession(row.id)
          firestore.collection("users").document(current.uid).collection("workoutSessions").document(row.id)
            .set(row.toFirestoreMap() + mapOf("sets" to sets.map { it.toFirestoreMap() }))
            .await()
          dao.markSessionSynced(row.id)
        }
        progress(4, "マシン情報取得")
        val machineSnapshot = firestore.collection("machines").get().await()
        val remoteMachines =
          machineSnapshot.documents.mapNotNull { doc ->
            val imageStorageUrl = doc.getString("imageStorageUrl")
            MachineEntity(
              id = doc.id,
              number = doc.machineNumberString() ?: return@mapNotNull null,
              name = doc.getString("name") ?: return@mapNotNull null,
              bodyPart = doc.getString("bodyPart") ?: "",
              icon = doc.getString("icon") ?: "🏋",
              targetSets = (doc.getLong("targetSets") ?: 3).toInt(),
              defaultWeight = (doc.getLong("defaultWeight") ?: 40).toInt(),
              imageStorageUrl = imageStorageUrl,
              localImagePath =
                imageStorageUrl?.let {
                  progress(5, "マシン画像取得")
                  downloadMachineImageIfExists(doc.id, it)
                },
              updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
            )
          }
        dao.clearMachines()
        if (remoteMachines.isNotEmpty()) dao.upsertMachines(remoteMachines)
        context.sessionStore.edit { it[lastSyncKey] = System.currentTimeMillis() }
        progress(6, "完了")
        "同期しました。アップロードに成功しました。"
      }
    }

  private fun isOnline(): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  private suspend fun downloadMachineImageIfExists(machineId: String, imageStorageUrl: String): String? =
    storageReferencesFromUrl(imageStorageUrl).firstNotNullOfOrNull { ref ->
      runCatching {
        ref.metadata.await()
        val extension = ref.name.substringAfterLast('.', "png").ifBlank { "png" }
        val imageDir = File(context.filesDir, "machine-images").apply { mkdirs() }
        val imageFile = File(imageDir, "$machineId.$extension")
        ref.getFile(imageFile).await()
        imageFile.absolutePath
      }.getOrNull()
    }

  private fun storageReferencesFromUrl(imageStorageUrl: String): List<StorageReference> {
    val trimmed = imageStorageUrl.trim()
    if (!trimmed.startsWith("gs://")) return listOf(storage.getReferenceFromUrl(trimmed))
    val withoutScheme = trimmed.removePrefix("gs://")
    val slashIndex = withoutScheme.indexOf('/')
    require(slashIndex > 0 && slashIndex < withoutScheme.lastIndex) { "Invalid Firebase Storage URL: $imageStorageUrl" }
    val bucket = withoutScheme.substring(0, slashIndex)
    val rawPath = withoutScheme.substring(slashIndex + 1)
    val objectPath = Uri.decode(rawPath)
    val bucketRef = FirebaseStorage.getInstance("gs://$bucket").reference
    return listOf(
      objectPath,
      Normalizer.normalize(objectPath, Normalizer.Form.NFC),
      Normalizer.normalize(objectPath, Normalizer.Form.NFD),
    ).distinct().map { path -> bucketRef.child(path) }
  }
}

class AppViewModel(private val repository: GymRepository) : androidx.lifecycle.ViewModel() {
  private val _state = MutableStateFlow(AppState())
  val state: StateFlow<AppState> = _state

  init {
    viewModelScope.launch { repository.session.collect { session -> _state.update { it.copy(session = session, sessionLoaded = true) } } }
    viewModelScope.launch {
      repository.machines.collect { machines ->
        _state.update {
          it.copy(
            machines = machines,
            selectedMachine =
              machines.firstOrNull { machine -> machine.id == it.selectedMachine.id }
                ?: machines.firstOrNull { machine -> it.selectedWorkoutMachineIds.contains(machine.id) }
                ?: machines.firstOrNull()
                ?: it.selectedMachine,
          ).withPreviousMenuDefault()
        }
      }
    }
    viewModelScope.launch { repository.sessions.collect { sessions -> _state.update { it.copy(sessions = sessions).withPreviousMenuDefault() } } }
    viewModelScope.launch { repository.savedSets.collect { sets -> _state.update { it.copy(savedSets = sets).withPreviousMenuDefault() } } }
    viewModelScope.launch { repository.playlists.collect { playlists -> _state.update { it.copy(playlists = playlists) } } }
    viewModelScope.launch { repository.allTracks.collect { tracks -> _state.update { it.copy(tracks = tracks) } } }
    viewModelScope.launch { repository.weightUnit.collect { unit -> _state.update { it.copy(weightUnit = unit) } } }
    viewModelScope.launch { repository.restVoiceVolume.collect { volume -> _state.update { it.copy(restVoiceVolume = volume) } } }
    viewModelScope.launch { repository.restAlarmVolume.collect { volume -> _state.update { it.copy(restAlarmVolume = volume) } } }
    viewModelScope.launch {
      combine(repository.activeWorkoutDraft, repository.activeWorkoutDraftSets) { draft, sets -> draft to sets }
        .collect { (draft, sets) -> restoreActiveWorkoutIfReady(draft, sets) }
    }
  }

  fun navigate(screen: Screen) {
    _state.update { current ->
      if (screen == Screen.TrainingMenu && current.hasActiveWorkout) {
        current.copy(screen = Screen.Training)
      } else {
        current.copy(screen = screen).withPreviousMenuDefault()
      }
    }
  }

  fun prepareTrainingMenuDefaults() {
    _state.update { current ->
      if (current.screen == Screen.TrainingMenu) current.withPreviousMenuDefault() else current
    }
  }

  fun setBodyCheck(check: BodyCheck) {
    _state.update { it.copy(bodyCheck = check) }
    persistActiveWorkoutDraft()
  }

  fun setRestDurationSeconds(seconds: Int) {
    val restDuration = seconds.coerceIn(1, 999)
    _state.update { it.copy(restDurationSeconds = restDuration) }
    persistActiveWorkoutDraft()
  }

  fun selectMachine(machine: Machine) {
    _state.update { it.copy(selectedMachine = machine) }
    persistActiveWorkoutDraft()
  }

  fun toggleWorkoutMachine(machine: Machine, checked: Boolean) {
    _state.update { current ->
      val selectedIds =
        if (checked) {
          (current.selectedWorkoutMachineIds + machine.id).distinct()
        } else {
          current.selectedWorkoutMachineIds - machine.id
        }
      current.copy(selectedWorkoutMachineIds = selectedIds)
    }
    persistActiveWorkoutDraft()
  }

  fun addWorkoutMachine(machine: Machine) {
    _state.update { current ->
      current.copy(
        selectedWorkoutMachineIds = (current.selectedWorkoutMachineIds + machine.id).distinct(),
        selectedMachine = machine,
      )
    }
    persistActiveWorkoutDraft()
  }

  private fun restoreActiveWorkoutIfReady(draft: ActiveWorkoutDraft?, sets: List<WorkoutSet>) {
    if (draft == null || sets.isEmpty()) return
    _state.update { current ->
      if (current.hasActiveWorkout) {
        current
      } else {
        val selectedIds =
          draft.selectedMachineIds.ifEmpty { sets.map { it.machineId }.distinct() }
        if (selectedIds.isEmpty()) {
          current
        } else {
          val selectedMachine =
            current.machines.firstOrNull { it.id == draft.selectedMachineId }
              ?: current.machines.firstOrNull { it.id == selectedIds.first() }
              ?: current.selectedMachine
          current.copy(
            workoutSets = sets.map { it.copy(sessionId = ACTIVE_WORKOUT_DRAFT_ID) },
            selectedWorkoutMachineIds = selectedIds,
            selectedMachine = selectedMachine,
            bodyCheck = draft.bodyCheck,
            workoutStartedAt = sets.minOfOrNull { it.completedAt },
            restRemaining = 0,
            restDurationSeconds = draft.restDurationSeconds,
            isRestAlarmRinging = false,
            resumeMusicAfterAlarm = false,
            screen = Screen.Training,
            message = "中断したトレーニングを復元しました",
          )
        }
      }
    }
  }

  private fun persistActiveWorkoutDraft(state: AppState = _state.value) {
    if (state.workoutSets.isEmpty()) return
    launchSafe {
      repository.saveActiveWorkoutDraft(
        ActiveWorkoutDraft(
          selectedMachineIds = state.selectedWorkoutMachineIds,
          selectedMachineId = state.selectedMachine.id,
          bodyCheck = state.bodyCheck,
          restDurationSeconds = state.restDurationSeconds,
        ),
        state.workoutSets,
      )
    }
  }

  fun startSelectedWorkout() {
    val current = _state.value
    val workoutMachines = current.workoutMachines
    if (workoutMachines.isEmpty()) {
      _state.update { it.copy(message = "今日トレーニングするマシンを選択してください") }
      return
    }
    _state.update {
      it.copy(
        workoutSets = emptyList(),
        workoutStartedAt = LocalDateTime.now(),
        selectedMachine = workoutMachines.first(),
        restRemaining = 0,
        restDurationSeconds = 50,
        isRestAlarmRinging = false,
        resumeMusicAfterAlarm = false,
        screen = Screen.BodyCheck,
      )
    }
  }

  fun togglePlay() {
    _state.update { current ->
      if (current.currentTrack == null) current else current.copy(isPlaying = !current.isPlaying)
    }
  }

  fun handleMediaPlayPauseCommand(playingWhenNotResting: Boolean? = null) {
    _state.update { current ->
      if (current.restRemaining > 0 || current.isRestAlarmRinging) {
        current.copy(
          restRemaining = 0,
          isRestAlarmRinging = false,
          isPlaying = current.currentTrack != null,
          resumeMusicAfterAlarm = false,
        )
      } else if (current.currentTrack == null) {
        current
      } else {
        current.copy(isPlaying = playingWhenNotResting ?: !current.isPlaying)
      }
    }
  }

  fun setPlaying(playing: Boolean) {
    _state.update { current ->
      if (current.currentTrack == null) {
        current
      } else {
        current.copy(
          isPlaying = playing,
          resumeMusicAfterAlarm = if (current.restRemaining > 0 && !playing) false else current.resumeMusicAfterAlarm,
        )
      }
    }
  }

  fun cyclePlaybackMode() {
    _state.update {
      it.copy(
        playbackMode =
          when (it.playbackMode) {
            PlaybackMode.PlaylistLoop -> PlaybackMode.SingleLoop
            PlaybackMode.SingleLoop -> PlaybackMode.Shuffle
            PlaybackMode.Shuffle -> PlaybackMode.PlaylistLoop
          },
      )
    }
  }

  fun playTrack(track: Track) {
    _state.update { current ->
      val queue = current.tracks.filter { it.playlistId == track.playlistId }.sortedBy { it.orderIndex }
      current.copy(currentTrack = track, currentPlaylistId = track.playlistId, playbackQueue = queue, isPlaying = true, playbackPositionMs = 0, playbackRequestId = current.playbackRequestId + 1, message = "再生中: ${track.title}")
    }
  }

  fun playPlaylist(playlistId: String, orderedTracks: List<Track> = emptyList()) {
    val playlistTracks = orderedTracks.ifEmpty { _state.value.tracks.filter { it.playlistId == playlistId }.sortedBy { it.orderIndex } }
    val first = playlistTracks.firstOrNull()
    if (first == null) {
      _state.update { it.copy(message = "プレイリストに曲がありません") }
      return
    }
    _state.update { it.copy(currentPlaylistId = playlistId, playbackQueue = playlistTracks, currentTrack = first, isPlaying = true, playbackPositionMs = 0, playbackRequestId = it.playbackRequestId + 1, message = "プレイリストを再生中") }
  }

  fun playNextTrack() {
    moveTrack(1)
  }

  fun playPreviousTrack() {
    moveTrack(-1)
  }

  fun handleMediaPreviousCommand() {
    if (!completeSelectedSetFromMediaPrevious()) {
      playPreviousTrack()
    }
  }

  fun onTrackEnded() {
    _state.update { current ->
      when (current.playbackMode) {
        PlaybackMode.SingleLoop -> current.copy(isPlaying = true, playbackPositionMs = 0, playbackRequestId = current.playbackRequestId + 1)
        PlaybackMode.PlaylistLoop -> current.nextTrackState(offset = 1, random = false)
        PlaybackMode.Shuffle -> current.nextTrackState(offset = 1, random = true)
      }
    }
  }

  private fun moveTrack(offset: Int) {
    _state.update { current ->
      current.nextTrackState(offset = offset, random = current.playbackMode == PlaybackMode.Shuffle && offset > 0)
    }
  }

  private fun AppState.nextTrackState(offset: Int, random: Boolean): AppState {
    val playlistId = currentPlaylistId.ifBlank { currentTrack?.playlistId.orEmpty() }
    val playlistTracks = playbackQueue.takeIf { it.isNotEmpty() && it.all { track -> track.playlistId == playlistId } } ?: tracks.filter { it.playlistId == playlistId }.sortedBy { it.orderIndex }
    if (playlistTracks.isEmpty()) return this
    val currentIndex = playlistTracks.indexOfFirst { it.id == currentTrack?.id }.takeIf { it >= 0 } ?: 0
    val nextIndex =
      if (random && playlistTracks.size > 1) {
        generateSequence { Random.nextInt(playlistTracks.size) }.first { it != currentIndex }
      } else {
        (currentIndex + offset + playlistTracks.size) % playlistTracks.size
      }
    return copy(currentPlaylistId = playlistId, currentTrack = playlistTracks[nextIndex], isPlaying = true, playbackPositionMs = 0, playbackRequestId = playbackRequestId + 1)
  }

  fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
    _state.update {
      it.copy(
        playbackPositionMs = positionMs.coerceAtLeast(0),
        playbackDurationMs = durationMs.takeIf { duration -> duration > 0 } ?: it.currentTrack?.durationMs ?: 0,
      )
    }
  }

  fun deletePlaylist(playlistId: String) {
    launchSafe {
      repository.deletePlaylist(playlistId)
      _state.update { current ->
        if (current.currentPlaylistId == playlistId) {
          current.copy(currentPlaylistId = "", playbackQueue = emptyList(), currentTrack = null, isPlaying = false, playbackPositionMs = 0, playbackDurationMs = 0, message = "プレイリストを削除しました")
        } else {
          current.copy(message = "プレイリストを削除しました")
        }
      }
    }
  }

  fun completeSet(weight: Int, reps: Int, restSeconds: Int = 50) {
    val machine = _state.value.selectedMachine
    val nextSet = _state.value.workoutSets.count { it.machineId == machine.id } + 1
    if (nextSet > machine.targetSets) return
    val set = WorkoutSet(sessionId = ACTIVE_WORKOUT_DRAFT_ID, machineId = machine.id, machineNumber = machine.number, machineName = machine.name, setIndex = nextSet, weightKg = displayWeightToLb(weight, _state.value.weightUnit), reps = reps)
    val restDuration = restSeconds.coerceIn(1, 999)
    _state.update {
      val startsRest = nextSet < machine.targetSets
      it.copy(
        workoutSets = it.workoutSets + set,
        restRemaining = if (startsRest) restDuration else 0,
        restDurationSeconds = restDuration,
        isRestAlarmRinging = false,
        resumeMusicAfterAlarm = startsRest && it.isPlaying,
        isPlaying = it.isPlaying,
        restStartAnnouncementId = if (startsRest) it.restStartAnnouncementId + 1 else it.restStartAnnouncementId,
        workoutCompleteAnnouncementId = if (startsRest) it.workoutCompleteAnnouncementId else it.workoutCompleteAnnouncementId + 1,
        workoutCompleteSetCount = if (startsRest) it.workoutCompleteSetCount else nextSet,
      )
    }
    persistActiveWorkoutDraft()
  }

  fun updateWorkoutSet(setId: String, weight: Int, reps: Int) {
    var updatedState: AppState? = null
    _state.update { current ->
      val updatedSets =
        current.workoutSets.map { set ->
          if (set.id == setId) {
            set.copy(weightKg = displayWeightToLb(weight, current.weightUnit), reps = reps)
          } else {
            set
          }
        }
      current.copy(workoutSets = updatedSets).also { updatedState = it }
    }
    updatedState?.let(::persistActiveWorkoutDraft)
  }

  private fun completeSelectedSetFromMediaPrevious(): Boolean {
    val current = _state.value
    val machine = current.selectedMachine
    val canCompleteSet =
      current.screen == Screen.Training &&
        current.currentTrack != null &&
        current.isPlaying &&
        current.restRemaining == 0 &&
        !current.isRestAlarmRinging &&
        current.workoutSets.count { it.machineId == machine.id } < machine.targetSets
    if (!canCompleteSet) return false

    completeSet(
      weight = displayWeightFromLb(defaultWeightLbForMachine(machine, current.sessions, current.savedSets), current.weightUnit),
      reps = 10,
      restSeconds = current.restDurationSeconds,
    )
    return true
  }

  fun tickRest() {
    _state.update {
      if (it.restRemaining <= 1) {
        it.copy(
          restRemaining = 0,
          isRestAlarmRinging = true,
          resumeMusicAfterAlarm = it.resumeMusicAfterAlarm,
          isPlaying = false,
        )
      } else {
        it.copy(restRemaining = it.restRemaining - 1)
      }
    }
  }

  fun endRestOrStopAlarm() {
    _state.update {
      if (it.isRestAlarmRinging) {
        it.copy(
          isRestAlarmRinging = false,
          isPlaying = it.resumeMusicAfterAlarm,
          resumeMusicAfterAlarm = false,
        )
      } else {
        it.copy(restRemaining = 0, resumeMusicAfterAlarm = false)
      }
    }
  }

  fun saveFinishedWorkout(result: BodyResult) {
    launchSafe {
      val current = state.value
      val endedAt = LocalDateTime.now()
      val startedAt = current.workoutStartedAt ?: current.workoutSets.minOfOrNull { it.completedAt } ?: endedAt
      val session =
        WorkoutSession(
          uid = current.session.uid.ifBlank { "local" },
          date = startedAt.toLocalDate(),
          startedAt = startedAt,
          systolic = current.bodyCheck.systolic,
          diastolic = current.bodyCheck.diastolic,
          pulse = current.bodyCheck.pulse,
          endedAt = endedAt,
          weightKg = result.weightKg.toDoubleOrNull(),
          bodyFatPercent = result.bodyFatPercent.toDoubleOrNull(),
          muscleMassKg = result.muscleMassKg.toDoubleOrNull(),
          bodyWaterPercent = result.bodyWaterPercent.toDoubleOrNull(),
          bmi = result.bmi.toDoubleOrNull(),
          basalMetabolism = result.basalMetabolism.toDoubleOrNull(),
          visceralFat = result.visceralFat.toDoubleOrNull(),
      )
      repository.saveWorkout(session, current.workoutSets.map { it.copy(sessionId = session.id) })
      repository.clearActiveWorkoutDraft()
      _state.update { it.copy(workoutSets = emptyList(), workoutStartedAt = null, selectedWorkoutMachineIds = emptyList(), message = "今日のトレーニングを保存しました", screen = Screen.History) }
    }
  }

  fun login(email: String, password: String) {
    launchSafe {
      repository.login(email, password)
        .onSuccess {
          _state.update { it.copy(message = "ログインしました", screen = Screen.Home) }
        }
        .onFailure { error -> _state.update { it.copy(message = error.message ?: "ログインできませんでした") } }
    }
  }

  fun logout() {
    launchSafe {
      repository.logout()
      _state.update { it.copy(message = "ログアウトしました") }
    }
  }

  fun sync() {
    launchSafe {
      val allTasks = listOf("通信確認", "ログイン確認", "クラウド削除", "未同期データ送信", "マシン情報取得", "マシン画像取得", "完了")
      _state.update {
        it.copy(
          syncDialog = SyncDialogState(visible = true, processing = true, title = "処理中", message = "同期を開始しました", progress = 0f, tasks = allTasks, currentTask = allTasks.first()),
        )
      }
      repository.sync { progress ->
        _state.update {
          it.copy(
            syncDialog =
              SyncDialogState(
                visible = true,
                processing = true,
                title = "処理中",
                message = progress.currentTask,
                progress = progress.progress,
                tasks = progress.tasks,
                currentTask = progress.currentTask,
              ),
          )
        }
      }.onSuccess { message ->
        _state.update {
          it.copy(syncDialog = SyncDialogState(visible = true, processing = false, title = "同期完了", message = message, progress = 1f, tasks = allTasks, currentTask = "完了"))
        }
      }.onFailure { error ->
        _state.update {
          it.copy(syncDialog = SyncDialogState(visible = true, processing = false, title = "同期失敗", message = error.message ?: "同期できませんでした", progress = 1f, tasks = allTasks, currentTask = "失敗"))
        }
      }
    }
  }

  fun dismissSyncDialog() {
    _state.update { it.copy(syncDialog = null) }
  }

  fun setWeightUnit(unit: WeightUnit) {
    launchSafe { repository.setWeightUnit(unit) }
  }

  fun setRestVoiceVolume(volume: Float) {
    launchSafe { repository.setRestVoiceVolume(volume) }
  }

  fun setRestAlarmVolume(volume: Float) {
    launchSafe { repository.setRestAlarmVolume(volume) }
  }

  fun deleteWorkoutSet(setId: String) {
    launchSafe {
      repository.deleteWorkoutSet(setId)
      _state.update { it.copy(message = "運動記録を削除しました") }
    }
  }

  fun deleteWorkoutSession(sessionId: String) {
    launchSafe {
      repository.deleteWorkoutSession(sessionId)
      _state.update { it.copy(message = "ワークアウトを削除しました") }
    }
  }

  fun editWorkoutSession(sessionId: String) {
    _state.update { it.copy(historyEditSessionId = sessionId, screen = Screen.HistoryEdit) }
  }

  fun cancelHistoryEdit() {
    _state.update { it.copy(historyEditSessionId = "", screen = Screen.History) }
  }

  fun saveHistoryEdit(session: WorkoutSession, sets: List<WorkoutSet>) {
    launchSafe {
      repository.saveWorkout(session.copy(synced = false), sets)
      _state.update { it.copy(historyEditSessionId = "", message = "履歴を更新しました", screen = Screen.History) }
    }
  }

  fun createPlaylistFromFolder(name: String, folderUri: String, tracks: List<Track>) {
    launchSafe {
      repository.createPlaylist(name, folderUri, tracks)
      _state.update { it.copy(message = "${tracks.size}曲を読み込みました") }
    }
  }

  fun saveTrackOrder(tracks: List<Track>) {
    launchSafe {
      repository.reorderTracks(tracks)
      _state.update { it.copy(message = "曲順を保存しました") }
    }
  }
}

data class AppState(
  val session: UserSession = UserSession(),
  val sessionLoaded: Boolean = false,
  val machines: List<Machine> = emptyList(),
  val sessions: List<WorkoutSession> = emptyList(),
  val savedSets: List<WorkoutSet> = emptyList(),
  val playlists: List<Playlist> = emptyList(),
  val tracks: List<Track> = emptyList(),
  val currentPlaylistId: String = "",
  val playbackQueue: List<Track> = emptyList(),
  val currentTrack: Track? = null,
  val playbackRequestId: Int = 0,
  val playbackPositionMs: Long = 0,
  val playbackDurationMs: Long = 0,
  val playbackMode: PlaybackMode = PlaybackMode.PlaylistLoop,
  val screen: Screen = Screen.Home,
  val historyEditSessionId: String = "",
  val bodyCheck: BodyCheck = BodyCheck(),
  val message: String = "",
  val syncDialog: SyncDialogState? = null,
  val weightUnit: WeightUnit = WeightUnit.LB,
  val restVoiceVolume: Float = 1f,
  val restAlarmVolume: Float = 100f,
  val workoutSets: List<WorkoutSet> = emptyList(),
  val workoutStartedAt: LocalDateTime? = null,
  val selectedWorkoutMachineIds: List<String> = emptyList(),
  val selectedMachine: Machine = Machine("none", "", "", "", "", 0, 0),
  val restRemaining: Int = 0,
  val restDurationSeconds: Int = 50,
  val restStartAnnouncementId: Int = 0,
  val workoutCompleteAnnouncementId: Int = 0,
  val workoutCompleteSetCount: Int = 0,
  val isRestAlarmRinging: Boolean = false,
  val resumeMusicAfterAlarm: Boolean = false,
  val isPlaying: Boolean = false,
) {
  val workoutMachines: List<Machine>
    get() = machines.filter { selectedWorkoutMachineIds.contains(it.id) }

  val hasActiveWorkout: Boolean
    get() = selectedWorkoutMachineIds.isNotEmpty() || workoutSets.isNotEmpty()

  val hasCompletedWorkoutToday: Boolean
    get() = sessions.any { it.date == LocalDate.now() }
}

private fun AppState.withPreviousMenuDefault(): AppState {
  if (screen != Screen.TrainingMenu) return this
  if (selectedWorkoutMachineIds.isNotEmpty() || workoutSets.isNotEmpty()) return this
  val lastSession = sessions.maxByOrNull { it.startedAt } ?: return this
  val previousMachineIds =
    savedSets
      .filter { it.sessionId == lastSession.id }
      .map { it.machineId }
      .distinct()
      .filter { id -> machines.any { it.id == id } }
  if (previousMachineIds.isEmpty()) return this
  val firstMachine = machines.firstOrNull { it.id == previousMachineIds.first() } ?: selectedMachine
  return copy(selectedWorkoutMachineIds = previousMachineIds, selectedMachine = firstMachine)
}

private fun androidx.lifecycle.ViewModel.launchSafe(block: suspend () -> Unit) {
  viewModelScope.launch { block() }
}

private fun Machine.toEntity() = MachineEntity(id, number, name, bodyPart, icon, targetSets, defaultWeight, imageStorageUrl, localImagePath, updatedAt)
private fun Playlist.toEntity() = PlaylistEntity(id, name, folderUri, createdAt)
private fun Track.toEntity() = TrackEntity(id, playlistId, uri, title, artist, durationMs, orderIndex)
private fun ActiveWorkoutDraft.toEntity() =
  ActiveWorkoutDraftEntity(
    id = ACTIVE_WORKOUT_DRAFT_ID,
    selectedMachineIds = selectedMachineIds.joinToString(","),
    selectedMachineId = selectedMachineId,
    systolic = bodyCheck.systolic,
    diastolic = bodyCheck.diastolic,
    pulse = bodyCheck.pulse,
    restDurationSeconds = restDurationSeconds,
    updatedAt = System.currentTimeMillis(),
  )
private fun WorkoutSession.toEntity() =
  WorkoutSessionEntity(
    id,
    uid,
    date.toString(),
    startedAt.toString(),
    endedAt?.toString(),
    systolic,
    diastolic,
    pulse,
    bodyFatPercent,
    muscleMassKg,
    bodyWaterPercent,
    weightKg,
    bmi,
    basalMetabolism,
    visceralFat,
    synced,
  )
private fun WorkoutSet.toEntity() = WorkoutSetEntity(id, sessionId, machineId, machineNumber, machineName, setIndex, weightKg, reps, completedAt.toString())

private fun WorkoutSessionEntity.toFirestoreMap() =
  mapOf(
    "uid" to uid,
    "date" to date,
    "startedAt" to startedAt,
    "endedAt" to endedAt,
    "systolic" to systolic,
    "diastolic" to diastolic,
    "pulse" to pulse,
    "weightKg" to weightKg,
    "bodyFatPercent" to bodyFatPercent,
    "muscleMassKg" to muscleMassKg,
    "bodyWaterPercent" to bodyWaterPercent,
    "bmi" to bmi,
    "basalMetabolism" to basalMetabolism,
    "visceralFat" to visceralFat,
    "syncedAt" to System.currentTimeMillis(),
  )

private fun WorkoutSetEntity.toFirestoreMap() =
  mapOf("machineId" to machineId, "machineNumber" to machineNumber, "machineName" to machineName, "setIndex" to setIndex, "weightLb" to weightKg, "storedWeightUnit" to WeightUnit.LB.label, "reps" to reps, "completedAt" to completedAt)

private fun com.google.firebase.firestore.DocumentSnapshot.machineNumberString(): String? =
  when (val value = get("number")) {
    is String -> value.trim().ifBlank { null }
    is Number -> value.toLong().toString()
    else -> null
  }
