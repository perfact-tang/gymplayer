package com.vibecodingjapan.gymplayer.ui

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.documentfile.provider.DocumentFile
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.vibecodingjapan.gymplayer.AppState
import com.vibecodingjapan.gymplayer.AppViewModel
import com.vibecodingjapan.gymplayer.BodyCheck
import com.vibecodingjapan.gymplayer.BodyResult
import com.vibecodingjapan.gymplayer.GymRepository
import com.vibecodingjapan.gymplayer.Machine
import com.vibecodingjapan.gymplayer.PlaybackMode
import com.vibecodingjapan.gymplayer.R
import com.vibecodingjapan.gymplayer.Screen
import com.vibecodingjapan.gymplayer.Track
import com.vibecodingjapan.gymplayer.WeightUnit
import com.vibecodingjapan.gymplayer.displayWeightFromLb
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF07111C)
private val Panel = Color(0xFF111C2A)
private val Panel2 = Color(0xFF182536)
private val Panel3 = Color(0xFF202D3F)
private val Line = Color(0xFF2B3A50)
private val Blue = Color(0xFF5E7CFF)
private val Green = Color(0xFF62E77A)
private val Cyan = Color(0xFF27D7C1)
private val Muted = Color(0xFFA4B0BE)
private val Gold = Color(0xFFFFC947)
private val Danger = Color(0xFFFF756F)

@Composable
fun GymPlayerTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme =
      darkColorScheme(
        primary = Blue,
        secondary = Green,
        background = Ink,
        surface = Panel,
        onPrimary = Color.White,
        onSecondary = Color(0xFF06140B),
        onBackground = Color.White,
        onSurface = Color.White,
      ),
    content = { CompositionLocalProvider(LocalContentColor provides Color.White, content = content) },
  )
}

class GymPlayerAndroidViewModel(application: Application) : AndroidViewModel(application) {
  val inner = AppViewModel(GymRepository(application))
}

@Composable
fun GymPlayerApp(viewModel: GymPlayerAndroidViewModel = viewModel()) {
  val context = LocalContext.current
  val player = remember { ExoPlayer.Builder(context).build() }
  val appViewModel = viewModel.inner
  val state by appViewModel.state.collectAsStateWithLifecycleCompat()
  val mediaSession =
    remember(player, appViewModel) {
      MediaSession.Builder(context, player)
        .setCallback(
          object : MediaSession.Callback {
            override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
              val event = intent.mediaKeyEvent ?: return false
              if (event.action != KeyEvent.ACTION_DOWN) return true
              when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> appViewModel.setPlaying(true)
                KeyEvent.KEYCODE_MEDIA_PAUSE -> appViewModel.setPlaying(false)
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> appViewModel.togglePlay()
                KeyEvent.KEYCODE_MEDIA_NEXT -> appViewModel.playNextTrack()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> appViewModel.playPreviousTrack()
                else -> return false
              }
              return true
            }
          },
        )
        .build()
    }
  DisposableEffect(Unit) {
    onDispose {
      mediaSession.release()
      player.release()
    }
  }
  DisposableEffect(player) {
    val listener =
      object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          if (playbackState == Player.STATE_ENDED) appViewModel.onTrackEnded()
        }
      }
    player.addListener(listener)
    onDispose { player.removeListener(listener) }
  }
  LaunchedEffect(state.sessionLoaded, state.session.loggedIn, state.screen) {
    if (state.sessionLoaded && !state.session.loggedIn && state.screen != Screen.Settings) {
      appViewModel.navigate(Screen.Settings)
    }
  }
  LaunchedEffect(state.isPlaying) {
    if (state.isPlaying) player.play() else player.pause()
  }
  LaunchedEffect(state.playbackMode) {
    player.repeatMode =
      when (state.playbackMode) {
        PlaybackMode.PlaylistLoop -> Player.REPEAT_MODE_OFF
        PlaybackMode.SingleLoop -> Player.REPEAT_MODE_ONE
        PlaybackMode.Shuffle -> Player.REPEAT_MODE_OFF
      }
    player.shuffleModeEnabled = false
  }
  LaunchedEffect(state.currentTrack?.uri, state.playbackRequestId) {
    val uri = state.currentTrack?.uri.orEmpty()
    if (uri.isNotBlank()) {
      player.setMediaItem(MediaItem.fromUri(uri))
      player.prepare()
      if (state.isPlaying) player.play()
    } else {
      player.stop()
      player.clearMediaItems()
    }
  }
  LaunchedEffect(state.currentTrack?.uri) {
    while (state.currentTrack != null) {
      appViewModel.updatePlaybackProgress(player.currentPosition, player.duration)
      delay(500)
    }
  }
  LaunchedEffect(state.restRemaining) {
    if (state.restRemaining > 0) {
      delay(1000)
      appViewModel.tickRest()
    }
  }
  LaunchedEffect(state.isRestAlarmRinging) {
    if (state.isRestAlarmRinging) {
      val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
      try {
        while (true) {
          tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180)
          delay(420)
        }
      } finally {
        tone.stopTone()
        tone.release()
      }
    }
  }
  Row(
    Modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(Color(0xFF06101B), Color(0xFF0A1625), Color(0xFF07111C))))
      .padding(18.dp),
  ) {
    NavigationRail(state.screen, state.session.loggedIn, appViewModel::navigate)
    Spacer(Modifier.width(18.dp))
    Column(Modifier.weight(1f).fillMaxHeight()) {
      TopStatus(state, appViewModel)
      Spacer(Modifier.height(14.dp))
      MainContent(state, appViewModel)
    }
  }
  state.syncDialog?.takeIf { it.visible }?.let { dialog ->
    AlertDialog(
      onDismissRequest = { if (!dialog.processing) appViewModel.dismissSyncDialog() },
      title = { Text(dialog.title) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(dialog.message)
          LinearProgressIndicator(progress = { dialog.progress }, modifier = Modifier.fillMaxWidth(), color = Green)
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            dialog.tasks.forEach { task ->
              val prefix = if (task == dialog.currentTask && dialog.processing) "•" else "✓"
              Text("$prefix $task", color = if (task == dialog.currentTask) Color.White else Muted)
            }
          }
        }
      },
      confirmButton = {
        if (!dialog.processing) {
          Button(onClick = { appViewModel.dismissSyncDialog() }, shape = RoundedCornerShape(8.dp)) {
            Text("OK")
          }
        }
      },
      containerColor = Panel,
      titleContentColor = Color.White,
      textContentColor = Color.White,
    )
  }
}

@Composable
private fun NavigationRail(current: Screen, loggedIn: Boolean, onNavigate: (Screen) -> Unit) {
  val items =
    listOf(
      Screen.Home to ("⌂" to "ホーム"),
      Screen.Music to ("♫" to "ミュージック"),
      Screen.TrainingMenu to ("🏋" to "トレーニング"),
      Screen.History to ("📅" to "履歴"),
      Screen.Settings to ("⚙" to "設定"),
    )
  Column(
    Modifier.width(112.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color(0xCC0A1524)).border(1.dp, Line, RoundedCornerShape(8.dp)).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    items.forEach { (screen, pair) ->
      val selected = current == screen
      Column(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(if (selected) Color(0xFF162B55) else Color.Transparent)
          .clickable { onNavigate(if (!loggedIn && screen != Screen.Settings) Screen.Settings else screen) }
          .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          pair.first,
          fontSize =
            when (screen) {
              Screen.TrainingMenu -> 31.sp
              Screen.History -> 32.sp
              else -> 28.sp
            },
          lineHeight = 32.sp,
          color = if (selected) Color(0xFF89A7FF) else Color.White,
        )
        Text(pair.second, fontSize = 12.sp, color = if (selected) Color(0xFF89A7FF) else Muted, maxLines = 1)
      }
    }
  }
}

@Composable
private fun TopStatus(state: AppState, vm: AppViewModel) {
  Row(Modifier.fillMaxWidth().height(78.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    GlassPanel(Modifier.width(230.dp).fillMaxHeight().clickable { if (state.hasActiveWorkout) vm.navigate(Screen.TrainingMenu) }, contentPadding = 14.dp) {
      val workoutMachines = state.workoutMachines
      val completed = workoutMachines.count { machine -> state.workoutSets.count { it.machineId == machine.id } >= machine.targetSets }
      Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text("✓ 完成状況", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text("$completed", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Green, maxLines = 1)
        Text(" / ${workoutMachines.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
      }
    }
    MusicBar(state, vm, Modifier.weight(1f))
    Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    if (state.restRemaining > 0 || state.isRestAlarmRinging) {
      Text("休憩 ${state.restRemaining}s", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Gold, maxLines = 1)
    }
  }
}

@Composable
private fun MusicBar(state: AppState, vm: AppViewModel, modifier: Modifier = Modifier) {
  val hasTrack = state.currentTrack != null
  val duration = state.playbackDurationMs.takeIf { it > 0 } ?: state.currentTrack?.durationMs ?: 0
  val progress = if (duration > 0) (state.playbackPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
  GlassPanel(modifier.fillMaxHeight(), contentPadding = 12.dp) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(playbackModeIcon(state.playbackMode), fontSize = 21.sp, color = Color.White, modifier = Modifier.clickable { vm.cyclePlaybackMode() })
      Text("⏮", fontSize = 21.sp, color = if (hasTrack) Color.White else Muted, modifier = Modifier.clickable(enabled = hasTrack) { vm.playPreviousTrack() })
      Text(if (state.isPlaying) "⏸" else "▶", fontSize = 25.sp, color = if (hasTrack) Color.White else Muted, modifier = Modifier.clickable(enabled = hasTrack) { vm.togglePlay() })
      Text("⏭", fontSize = 21.sp, color = if (hasTrack) Color.White else Muted, modifier = Modifier.clickable(enabled = hasTrack) { vm.playNextTrack() })
      Column(Modifier.weight(1f)) {
        Text(state.currentTrack?.title.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(state.currentTrack?.artist.orEmpty(), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Text(if (hasTrack) formatDuration(state.playbackPositionMs) else "", color = Color.White, fontSize = 13.sp)
      Box(Modifier.width(170.dp).height(5.dp).clip(RoundedCornerShape(100.dp)).background(Line)) {
        if (hasTrack) Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Green))
      }
      Text(if (hasTrack) formatDuration(duration) else "", color = Muted, fontSize = 13.sp)
    }
  }
}

private fun formatDuration(durationMs: Long): String =
  "%02d:%02d".format(durationMs.coerceAtLeast(0) / 60000, (durationMs.coerceAtLeast(0) / 1000) % 60)

@Suppress("DEPRECATION")
private val Intent.mediaKeyEvent: KeyEvent?
  get() = getParcelableExtra(Intent.EXTRA_KEY_EVENT)

private fun playbackModeIcon(mode: PlaybackMode): String =
  when (mode) {
    PlaybackMode.PlaylistLoop -> "↻"
    PlaybackMode.SingleLoop -> "①"
    PlaybackMode.Shuffle -> "⤨"
  }

@Composable
private fun MainContent(state: AppState, vm: AppViewModel) {
  Box(Modifier.fillMaxSize()) {
    when (state.screen) {
      Screen.Home -> HomeScreen(state, vm)
      Screen.Music -> MusicScreen(state, vm)
      Screen.TrainingMenu -> TrainingMenuScreen(state, vm)
      Screen.BodyCheck -> BodyCheckScreen(state, vm)
      Screen.Training -> TrainingScreen(state, vm)
      Screen.FinishBody -> FinishBodyScreen(state, vm)
      Screen.History -> HistoryScreen(state, vm)
      Screen.Settings -> SettingsScreen(state, vm)
    }
  }
}

@Composable
private fun HomeScreen(state: AppState, vm: AppViewModel) {
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
      HeroCard(R.drawable.home_music_card, "ミュージック", "プレイリスト再生", "音楽を楽しもう", Modifier.weight(1f)) { vm.navigate(Screen.Music) }
      HeroCard(R.drawable.home_training_card, "トレーニング", "今日のメニューを確認", "トレーニングを記録", Modifier.weight(1f)) { vm.navigate(Screen.TrainingMenu) }
    }
    Button(
      onClick = { vm.navigate(if (state.session.loggedIn) Screen.TrainingMenu else Screen.Settings) },
      modifier = Modifier.fillMaxWidth().height(76.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
      shape = RoundedCornerShape(8.dp),
    ) {
      Text("▶ スタート", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
    Text("☁ クラウド同期 : ${if (state.session.lastSyncAt > 0) "最新" else "未同期"}", color = Muted, fontSize = 13.sp)
  }
}

@Composable
private fun HeroCard(imageRes: Int, title: String, line1: String, line2: String, modifier: Modifier, onClick: () -> Unit) {
  Card(
    modifier.fillMaxHeight().clickable { onClick() },
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = Panel),
    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
  ) {
    Box(Modifier.fillMaxSize()) {
      Image(
        painter = painterResource(imageRes),
        contentDescription = title,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
      Box(
        Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              0f to Color(0x22000000),
              0.5f to Color(0x3307111C),
              1f to Color(0xEE07111C),
            ),
          ),
      )
      Column(Modifier.align(Alignment.BottomStart).padding(26.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Text(line1, color = Color(0xFFD5DCE7), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(line2, color = Muted, fontSize = 15.sp)
      }
    }
  }
}

@Composable
private fun MusicScreen(state: AppState, vm: AppViewModel) {
  val context = LocalContext.current
  var newName by remember { mutableStateOf("") }
  var selectedPlaylistId by remember(state.playlists) { mutableStateOf(state.playlists.firstOrNull()?.id.orEmpty()) }
  var orderedTracks by remember(selectedPlaylistId, state.tracks) {
    mutableStateOf(state.tracks.filter { it.playlistId == selectedPlaylistId }.sortedBy { it.orderIndex })
  }
  val folderLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val folder = DocumentFile.fromTreeUri(context, uri)
        val tracks =
          folder
            ?.listFiles()
            ?.filter { it.isFile && it.type?.startsWith("audio/") == true }
            ?.sortedBy { it.name.orEmpty() }
            ?.mapIndexed { index, file ->
              Track(
                playlistId = "pending",
                uri = file.uri.toString(),
                title = file.name?.substringBeforeLast('.') ?: "Track ${index + 1}",
                artist = "Device folder",
                durationMs = 0,
                orderIndex = index,
              )
            }
            .orEmpty()
        vm.createPlaylistFromFolder(newName, uri.toString(), tracks)
      }
    }
  Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    GlassPanel(Modifier.width(292.dp).fillMaxHeight()) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("プレイリスト", fontSize = 22.sp, fontWeight = FontWeight.Bold)
          Text("+", fontSize = 26.sp, color = Color.White)
        }
        state.playlists.forEach { playlist ->
          val count = state.tracks.count { track -> track.playlistId == playlist.id }
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (playlist.id == selectedPlaylistId) Color(0x6632C770) else Color(0x2226B86A))
                .clickable { selectedPlaylistId = playlist.id }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("≡  ${playlist.name}\n   ${count}曲", fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
            Text("削除", color = Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { vm.deletePlaylist(playlist.id) })
          }
        }
        Spacer(Modifier.weight(1f))
        OutlinedTextField(newName, { newName = it }, label = { Text("新しいプレイリスト") }, singleLine = true)
        Button(onClick = { folderLauncher.launch(null) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(48.dp)) { Text("フォルダを選択", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Text("MP3 / WAV / M4A など audio/* として見えるファイルを自動で読み込みます。", color = Muted, fontSize = 12.sp)
      }
    }
    GlassPanel(Modifier.weight(1f).fillMaxHeight()) {
      Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column {
            Text(state.playlists.firstOrNull { it.id == selectedPlaylistId }?.name.orEmpty(), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
            Text("${orderedTracks.size}曲", color = Muted, fontSize = 13.sp)
          }
          Button(
            onClick = { vm.playPlaylist(selectedPlaylistId, orderedTracks) },
            enabled = selectedPlaylistId.isNotBlank() && orderedTracks.isNotEmpty(),
            shape = RoundedCornerShape(8.dp),
          ) {
            Text("▶ 再生", fontSize = 15.sp, fontWeight = FontWeight.Bold)
          }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(orderedTracks) { track ->
            TrackRow(
              track,
              onUp = {
                val index = orderedTracks.indexOf(track)
                if (index > 0) orderedTracks = orderedTracks.toMutableList().also { java.util.Collections.swap(it, index, index - 1) }
              },
              onDown = {
                val index = orderedTracks.indexOf(track)
                if (index in 0 until orderedTracks.lastIndex) orderedTracks = orderedTracks.toMutableList().also { java.util.Collections.swap(it, index, index + 1) }
              },
              onPlay = { vm.playTrack(track) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TrackRow(track: Track, onUp: () -> Unit, onDown: () -> Unit, onPlay: () -> Unit) {
  var dragY by remember { mutableStateOf(0f) }
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(Panel2)
      .pointerInput(track.id) {
        detectDragGesturesAfterLongPress(
          onDragEnd = { dragY = 0f },
          onDragCancel = { dragY = 0f },
          onDrag = { change, dragAmount ->
            change.consume()
            dragY += dragAmount.y
            if (dragY < -48f) {
              onUp()
              dragY = 0f
            } else if (dragY > 48f) {
              onDown()
              dragY = 0f
            }
          },
        )
      }
      .clickable { onPlay() }
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("⋮⋮", color = Muted, fontSize = 14.sp)
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Cyan, Blue, Color(0xFFE55A2A)))))
    Column(Modifier.weight(1f)) {
      Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text("%02d:%02d".format(track.durationMs / 60000, (track.durationMs / 1000) % 60), color = Muted, fontSize = 13.sp)
    Text("↑", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { onUp() })
    Text("↓", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { onDown() })
  }
}

@Composable
private fun TrainingMenuScreen(state: AppState, vm: AppViewModel) {
  LaunchedEffect(state.sessions, state.savedSets, state.machines) {
    vm.prepareTrainingMenuDefaults()
  }
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("今日のトレーニングメニュー", fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    Text("${LocalDate.now()}（水）  選択 ${state.selectedWorkoutMachineIds.size} / ${state.machines.size}", color = Muted, fontSize = 14.sp)
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
      items(state.machines) { machine ->
        val checked = state.selectedWorkoutMachineIds.contains(machine.id)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (checked) Color(0x2032C770) else Panel2).border(1.dp, if (checked) Color(0x6632C770) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { vm.toggleWorkoutMachine(machine, !checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("${machine.number}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(44.dp))
          Text(machine.icon, fontSize = 24.sp, modifier = Modifier.width(48.dp))
          Column(Modifier.weight(1f)) {
            Text(machine.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(machine.bodyPart, color = Muted, fontSize = 12.sp)
          }
          Text("${machine.targetSets}セット", fontSize = 16.sp, fontWeight = FontWeight.Bold)
          Spacer(Modifier.width(30.dp))
          Text("10回", fontSize = 16.sp, fontWeight = FontWeight.Bold)
          Spacer(Modifier.width(30.dp))
          Box(Modifier.width(60.dp), contentAlignment = Alignment.Center) {
            Checkbox(
              checked = checked,
              onCheckedChange = { vm.toggleWorkoutMachine(machine, it) },
              colors =
                CheckboxDefaults.colors(
                  checkedColor = Green,
                  uncheckedColor = Muted,
                  checkmarkColor = Color(0xFF06140B),
                ),
              modifier = Modifier.size(40.dp),
            )
          }
        }
      }
    }
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Button(
        onClick = { vm.startSelectedWorkout() },
        enabled = state.selectedWorkoutMachineIds.isNotEmpty(),
        modifier = Modifier.height(58.dp).width(292.dp),
        shape = RoundedCornerShape(8.dp),
      ) { Text("▶ トレーニング開始", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
    }
  }
}

@Composable
private fun BodyCheckScreen(state: AppState, vm: AppViewModel) {
  var high by remember { mutableStateOf(state.bodyCheck.systolic.toString()) }
  var low by remember { mutableStateOf(state.bodyCheck.diastolic.toString()) }
  var pulse by remember { mutableStateOf(state.bodyCheck.pulse.toString()) }
  val valid = high.isNotBlank() && low.isNotBlank() && pulse.isNotBlank()
  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
    Text("トレーニング前の身体チェック", fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    Text("安全にトレーニングを行うために数値を入力してください。", color = Muted, fontSize = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
      RequiredNumberCard("最高血圧", high) { high = it.filter(Char::isDigit).take(3) }
      RequiredNumberCard("最低血圧", low) { low = it.filter(Char::isDigit).take(3) }
      RequiredNumberCard("心拍数", pulse) { pulse = it.filter(Char::isDigit).take(3) }
    }
    Button(
      onClick = {
        vm.setBodyCheck(BodyCheck(high.toInt(), low.toInt(), pulse.toInt()))
        vm.navigate(Screen.Training)
      },
      enabled = valid,
      modifier = Modifier.size(160.dp),
      shape = CircleShape,
      colors = ButtonDefaults.buttonColors(containerColor = Blue),
    ) {
      Text("開始", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
    Text("ⓘ 体調がすぐれない場合は無理をせず、休憩を取りましょう。", color = Muted, fontSize = 13.sp)
  }
}

@Composable
private fun RequiredNumberCard(title: String, value: String, onValueChange: (String) -> Unit) {
  GlassPanel(Modifier.width(220.dp).height(198.dp), contentPadding = 16.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
      Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
      OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth(),
      )
      Text("必須入力", color = Muted, fontSize = 12.sp)
    }
  }
}

@Composable
private fun TrainingScreen(state: AppState, vm: AppViewModel) {
  var weight by remember(state.selectedMachine.id, state.weightUnit) { mutableStateOf(displayWeightFromLb(state.selectedMachine.defaultWeight, state.weightUnit).toString()) }
  var reps by remember(state.selectedMachine.id) { mutableStateOf("10") }
  var restSeconds by remember { mutableStateOf("50") }
  val workoutMachines = state.workoutMachines.ifEmpty { state.machines }
  val machineSets = state.workoutSets.filter { it.machineId == state.selectedMachine.id }
  Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    GlassPanel(Modifier.weight(0.92f).fillMaxHeight()) {
      Column {
        Text("マシン番号", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("今日選択したマシンだけを表示", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        MachineGrid(workoutMachines, state.selectedMachine, state.workoutSets, vm::selectMachine)
        Spacer(Modifier.weight(1f))
      }
    }
    GlassPanel(Modifier.weight(1.08f).fillMaxHeight()) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          "${state.selectedMachine.number}号機 / ${state.selectedMachine.name}",
          fontSize = 24.sp,
          lineHeight = 29.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          modifier = Modifier.fillMaxWidth(),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        MachineImage(state.selectedMachine, Modifier.size(190.dp))
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
          NumberInputStepper(
            title = "重量 (${state.weightUnit.label})",
            value = weight,
            onValueChange = { weight = it },
            minus = { weight = ((weight.toIntOrNull() ?: 0) - 5).coerceAtLeast(0).toString() },
            plus = { weight = ((weight.toIntOrNull() ?: 0) + 5).toString() },
          )
          NumberInputStepper(
            title = "回数",
            value = reps,
            onValueChange = { reps = it },
            minus = { reps = ((reps.toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString() },
            plus = { reps = ((reps.toIntOrNull() ?: 0) + 1).toString() },
          )
        }
        Spacer(Modifier.weight(1f))
        Button(
          onClick = {
            vm.completeSet(
              weight.toIntOrNull() ?: 0,
              reps.toIntOrNull() ?: 0,
              restSeconds.toIntOrNull() ?: 50,
            )
          },
          modifier = Modifier.fillMaxWidth().height(78.dp),
          enabled = machineSets.size < state.selectedMachine.targetSets,
          shape = RoundedCornerShape(8.dp),
        ) {
          Text("✓ このセットを完了", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Button(onClick = { vm.navigate(Screen.FinishBody) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Panel3)) {
          Text("終了", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      GlassPanel(Modifier.fillMaxWidth().height(158.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
          Column {
            val isRestActive = state.restRemaining > 0 || state.isRestAlarmRinging
            Text(
              when {
                state.isRestAlarmRinging -> "⏰ 休憩終了"
                state.restRemaining > 0 -> "☕ 休憩中"
                else -> "トレーニング中"
              },
              fontSize = 21.sp,
              fontWeight = FontWeight.Bold,
            )
            if (isRestActive) {
              Text("${state.restRemaining} / ${state.restDurationSeconds} 秒", fontSize = 42.sp, lineHeight = 46.sp, color = Green)
            } else {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("0 /", fontSize = 34.sp, color = Muted)
                OutlinedTextField(
                  value = restSeconds,
                  onValueChange = { restSeconds = it.filter(Char::isDigit).take(3) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  textStyle = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                  modifier = Modifier.width(96.dp).height(66.dp),
                )
                Text("秒", fontSize = 30.sp, color = Muted)
              }
            }
          }
          if (state.restRemaining > 0 || state.isRestAlarmRinging) {
            Button(
              onClick = { vm.endRestOrStopAlarm() },
              modifier = Modifier.size(66.dp),
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
            ) {
              Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
            }
          }
        }
      }
      GlassPanel(Modifier.weight(1f).fillMaxWidth()) {
        Column {
          Text("このマシンの履歴", fontSize = 20.sp, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(10.dp))
          (1..state.selectedMachine.targetSets).forEach { index ->
            val set = machineSets.getOrNull(index - 1)
            Text("$index    ${set?.let { displayWeightFromLb(it.weightKg, state.weightUnit) } ?: "–"} ${state.weightUnit.label} / ${set?.reps ?: "–"} 回    ${if (set != null) "✓" else "○"}", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (index == machineSets.size + 1) Color(0x553F63FF) else Panel2).padding(horizontal = 14.dp, vertical = 12.dp))
            Spacer(Modifier.height(8.dp))
          }
        }
      }
      GlassPanel(Modifier.fillMaxWidth().height(86.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
          Text("合計セット数", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
          Text("${state.selectedMachine.targetSets} 組", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Blue)
        }
      }
    }
  }
}

@Composable
private fun MachineImage(machine: Machine, modifier: Modifier = Modifier) {
  val path = machine.localImagePath
  val bitmap = remember(path) {
    path
      ?.takeIf { File(it).exists() }
      ?.let { BitmapFactory.decodeFile(it) }
  }
  Box(
    modifier
      .clip(RoundedCornerShape(8.dp))
      .background(Panel2)
      .border(1.dp, Line, RoundedCornerShape(8.dp)),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      Image(bitmap = bitmap.asImageBitmap(), contentDescription = machine.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
      Text(machine.icon, fontSize = 30.sp)
    }
  }
}

@Composable
private fun MachineGrid(machines: List<Machine>, current: Machine, sets: List<com.vibecodingjapan.gymplayer.WorkoutSet>, onSelect: (Machine) -> Unit) {
  val chunks = machines.chunked(3)
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
    chunks.forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        row.forEach { machine ->
          val done = sets.count { it.machineId == machine.id } >= machine.targetSets
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              Modifier
                .size(82.dp)
                .clip(CircleShape)
                .border(2.dp, if (machine.id == current.id) Blue else Line, CircleShape)
                .background(if (machine.id == current.id) Color(0x553F63FF) else Color.Transparent)
                .clickable { onSelect(machine) },
              contentAlignment = Alignment.Center,
            ) { Text("${machine.number}", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text(if (done) "✓" else if (machine.id == current.id) "現在" else "", color = if (done) Green else Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun NumberInputStepper(title: String, value: String, onValueChange: (String) -> Unit, minus: () -> Unit, plus: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
    Text(title, color = Muted, fontSize = 13.sp)
    OutlinedTextField(
      value = value,
      onValueChange = { onValueChange(it.filter(Char::isDigit).take(4)) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      textStyle = TextStyle(color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
      modifier = Modifier.width(148.dp).height(82.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Button(onClick = minus, shape = CircleShape, modifier = Modifier.size(54.dp), contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("−", fontSize = 24.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        }
      }
      Button(onClick = plus, shape = CircleShape, modifier = Modifier.size(54.dp), contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("+", fontSize = 24.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        }
      }
    }
  }
}

@Composable
private fun FinishBodyScreen(state: AppState, vm: AppViewModel) {
  var result by remember { mutableStateOf(BodyResult()) }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("今日の身体数値", fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
      BodyMetricInput("体重 (kg)", result.weightKg, { result = result.copy(weightKg = it) }, Modifier.weight(1f))
      BodyMetricInput("体脂肪率 (%)", result.bodyFatPercent, { result = result.copy(bodyFatPercent = it) }, Modifier.weight(1f))
      BodyMetricInput("筋肉量 (kg)", result.muscleMassKg, { result = result.copy(muscleMassKg = it) }, Modifier.weight(1f))
      BodyMetricInput("体水分率 (%)", result.bodyWaterPercent, { result = result.copy(bodyWaterPercent = it) }, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
      BodyMetricInput("BMI", result.bmi, { result = result.copy(bmi = it) }, Modifier.weight(1f))
      BodyMetricInput("基礎代謝量", result.basalMetabolism, { result = result.copy(basalMetabolism = it) }, Modifier.weight(1f))
      BodyMetricInput("内臓脂肪", result.visceralFat, { result = result.copy(visceralFat = it) }, Modifier.weight(1f))
      Spacer(Modifier.weight(1f))
    }
    Button(onClick = { vm.saveFinishedWorkout(result) }, modifier = Modifier.width(240.dp).height(56.dp), shape = RoundedCornerShape(8.dp)) { Text("保存", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
  }
}

@Composable
private fun BodyMetricInput(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
  OutlinedTextField(
    value = value,
    onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' }.take(8)) },
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
    modifier = modifier,
  )
}

@Composable
private fun HistoryScreen(state: AppState, vm: AppViewModel) {
  val initialDate = state.sessions.firstOrNull()?.date ?: LocalDate.now()
  var selectedDate by remember(state.sessions) { mutableStateOf(initialDate) }
  var displayedMonth by remember(selectedDate) { mutableStateOf(selectedDate.withDayOfMonth(1)) }
  val daySessions = state.sessions.filter { it.date == selectedDate }.sortedBy { it.startedAt }
  var selectedSessionId by remember(selectedDate, daySessions) { mutableStateOf(daySessions.firstOrNull()?.id.orEmpty()) }
  val selectedSession = daySessions.firstOrNull { it.id == selectedSessionId } ?: daySessions.firstOrNull()
  val selectedSessionSets = selectedSession?.let { session -> state.savedSets.filter { it.sessionId == session.id }.sortedBy { it.completedAt } }.orEmpty()
  Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    GlassPanel(Modifier.width(292.dp).fillMaxHeight()) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("履歴", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        CompactCalendarGrid(
          marked = state.sessions.map { it.date }.toSet(),
          month = displayedMonth,
          selected = selectedDate,
          onMonthChange = { month ->
            displayedMonth = month
            selectedDate = state.sessions.map { it.date }.filter { it.year == month.year && it.monthValue == month.monthValue }.minOrNull() ?: month
          },
        ) { selectedDate = it }
        Text("選択日: $selectedDate", color = Muted, fontSize = 13.sp)
      }
    }
    GlassPanel(Modifier.weight(1f).fillMaxHeight()) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("完了したトレーニング", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${daySessions.size} 件", color = Muted, fontSize = 13.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(daySessions) { session ->
            val sets = state.savedSets.filter { it.sessionId == session.id }
            Row(
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (session.id == selectedSession?.id) Color(0x553F63FF) else Panel2)
                .clickable { selectedSessionId = session.id }
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Column(Modifier.weight(1f)) {
                Text("ID ${session.id.take(8)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${session.startedAt.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${session.endedAt?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"} 完了 / ${sets.size} セット", color = Muted, fontSize = 12.sp)
              }
              Text("削除", color = Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { vm.deleteWorkoutSession(session.id) })
            }
          }
        }
      }
    }
    GlassPanel(Modifier.weight(1f).fillMaxHeight()) {
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("トレーニング詳細", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (selectedSession == null) {
          Text("この日に運動記録はありません。", color = Muted, fontSize = 14.sp)
        } else {
          Text("ID ${selectedSession.id}", color = Muted, fontSize = 12.sp)
          Text("トレーニング前", fontSize = 18.sp, fontWeight = FontWeight.Bold)
          SummaryLine("血圧", "${selectedSession.systolic} / ${selectedSession.diastolic}")
          SummaryLine("心拍数", "${selectedSession.pulse} bpm")
          SummaryLine("開始時間", selectedSession.startedAt.format(DateTimeFormatter.ofPattern("HH:mm")))
          Spacer(Modifier.height(8.dp))
          Text("セット記録", fontSize = 18.sp, fontWeight = FontWeight.Bold)
          selectedSessionSets.groupBy { it.machineId }.values.forEach { machineSets ->
            val first = machineSets.first()
            Text("${first.machineNumber}号機 / ${first.machineName}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            machineSets.sortedBy { it.setIndex }.forEach { set ->
              SummaryLine(
                "${set.setIndex}セット目",
                "${displayWeightFromLb(set.weightKg, WeightUnit.KG)} kg / ${set.reps} 回 / ${set.completedAt.format(DateTimeFormatter.ofPattern("HH:mm"))} 完了",
              )
            }
          }
          if (selectedSessionSets.isEmpty()) {
            Text("セット記録はありません。", color = Muted, fontSize = 14.sp)
          }
          Spacer(Modifier.height(8.dp))
          Text("トレーニング後", fontSize = 18.sp, fontWeight = FontWeight.Bold)
          SummaryLine("終了時間", selectedSession.endedAt?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "-")
          SummaryLine("体重", selectedSession.weightKg?.let { "%.1f kg".format(it) } ?: "-")
          SummaryLine("体脂肪率", selectedSession.bodyFatPercent?.let { "%.1f %%".format(it) } ?: "-")
          SummaryLine("筋肉量", selectedSession.muscleMassKg?.let { "%.1f kg".format(it) } ?: "-")
          SummaryLine("体水分率", selectedSession.bodyWaterPercent?.let { "%.1f %%".format(it) } ?: "-")
          SummaryLine("BMI", selectedSession.bmi?.let { "%.1f".format(it) } ?: "-")
          SummaryLine("基礎代謝量", selectedSession.basalMetabolism?.let { "%.0f".format(it) } ?: "-")
          SummaryLine("内臓脂肪", selectedSession.visceralFat?.let { "%.1f".format(it) } ?: "-")
          SummaryLine("合計マシン数", "${selectedSessionSets.groupBy { it.machineId }.size}")
          SummaryLine("合計セット数", "${selectedSessionSets.size}")
          Button(
            onClick = { vm.deleteWorkoutSession(selectedSession.id) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White),
          ) {
            Text("この記録を削除", fontSize = 15.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun CompactCalendarGrid(
  marked: Set<LocalDate>,
  month: LocalDate,
  selected: LocalDate,
  onMonthChange: (LocalDate) -> Unit,
  onSelect: (LocalDate) -> Unit,
) {
  val today = LocalDate.now()
  val first = month.withDayOfMonth(1)
  val leadingBlanks = first.dayOfWeek.value - 1
  val monthDays = (1..first.lengthOfMonth()).map { first.withDayOfMonth(it) }
  val cells = (List<LocalDate?>(leadingBlanks) { null } + monthDays).let { days ->
    days + List((7 - days.size % 7) % 7) { null }
  }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(42.dp).clickable { onMonthChange(first.minusMonths(1)) })
      Text("${first.year}年${first.monthValue}月", fontSize = 18.sp, fontWeight = FontWeight.Bold)
      Text("›", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(42.dp).clickable { onMonthChange(first.plusMonths(1)) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf("月", "火", "水", "木", "金", "土", "日").forEach { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    cells.chunked(7).forEach { week ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEach { day ->
          if (day == null) {
            Spacer(Modifier.size(32.dp))
          } else {
            val isSelected = day == selected
            Box(Modifier.size(32.dp).clip(CircleShape).background(if (isSelected) Blue else if (day == today) Green else Color.Transparent).clickable { onSelect(day) }, contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${day.dayOfMonth}", color = if (isSelected || day == today) Color(0xFF06140B) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (marked.contains(day)) "•" else "", color = Green, fontSize = 10.sp)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SummaryLine(label: String, value: String) {
  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Panel2).border(1.dp, Line, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = Muted, fontSize = 13.sp)
    Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
  }
}

@Composable
private fun SettingsScreen(state: AppState, vm: AppViewModel) {
  var email by remember { mutableStateOf(state.session.email.ifBlank { "name@example.com" }) }
  var password by remember { mutableStateOf("") }
  Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    GlassPanel(Modifier.width(390.dp).fillMaxHeight()) {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ログイン", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("アカウントにログインして、データをクラウドに同期します。", color = Muted, fontSize = 14.sp)
        OutlinedTextField(email, { email = it }, label = { Text("メールアドレス") }, singleLine = true)
        OutlinedTextField(password, { password = it }, label = { Text("パスワード") }, singleLine = true)
        Button(onClick = { vm.login(email, password) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(8.dp)) { Text("ログイン", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Panel3)) { Text("ログアウト", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
      }
    }
    GlassPanel(Modifier.weight(1f).fillMaxHeight()) {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("同期", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("ログイン状態: ${if (state.session.loggedIn) "ログイン済み" else "未ログイン"}", fontSize = 15.sp)
        Text("UserID: ${state.session.uid.ifBlank { "-" }}", color = Muted, fontSize = 13.sp)
        Button(onClick = { vm.sync() }, modifier = Modifier.width(230.dp).height(54.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White)) { Text("同期する", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        Text("アプリ起動時はネットワーク確認を行わず、ログアウトするまでローカル保存データで利用できます。", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Text("重量単位", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("マシン重量だけに適用します。利用者の体重は常に kg で保存・表示します。", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          WeightUnit.values().forEach { unit ->
            val selected = state.weightUnit == unit
            Button(
              onClick = { vm.setWeightUnit(unit) },
              modifier = Modifier.width(128.dp).height(50.dp),
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(containerColor = if (selected) Green else Panel3, contentColor = Color.White),
            ) {
              Text(unit.label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, contentPadding: Dp = 20.dp, content: @Composable () -> Unit) {
  Card(modifier, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xD6111C2A)), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
    Box(Modifier.fillMaxSize().padding(contentPadding)) { content() }
  }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleCompat(): androidx.compose.runtime.State<T> {
  return collectAsStateWithLifecycle()
}
