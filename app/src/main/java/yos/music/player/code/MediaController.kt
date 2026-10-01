package yos.music.player.code

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.MainActivity
import yos.music.player.R
import yos.music.player.code.MediaController.mediaControl
import yos.music.player.code.MediaController.mediaSession
import yos.music.player.code.MediaController.musicPlaying
import yos.music.player.code.MediaController.onServiceRunning
import yos.music.player.code.utils.lrc.YosLrcFactory
import yos.music.player.code.utils.lrc.YosTtmlFactory
import yos.music.player.code.utils.player.FadeExo
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.code.utils.player.FadeExo.fadePlay
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.MusicLibrary.toMediaItem
import yos.music.player.data.libraries.MusicLibrary.toYosMediaItem
import yos.music.player.data.libraries.PlayListV1
import yos.music.player.data.libraries.PlayStatus
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.uri
import yos.music.player.data.objects.MainViewModelObject
import yos.music.player.data.objects.MediaViewModelObject

@Stable
object MediaController {
    @Stable
    val mainMusicList: List<YosMediaItem>
        get() = MusicLibrary.songs

    @Stable
    var playingMusicList = mutableStateOf<List<YosMediaItem>?>(null)

    @Stable
    var mediaControl: MediaController? = null

    @Stable
    var musicPlaying = mutableStateOf<YosMediaItem?>(null)

    @Stable
    var mediaSession: MediaSession? = null

    fun onServiceRunning() {
        val handler by lazy { Handler(Looper.getMainLooper()) }
        var lastLyric = listOf<Pair<Float, String>>()

        val updateLyricsRunnable = object : Runnable {
            override fun run() {
                runCatching {
                    var currentLyricIndex: Int
                    var isPlaying: Boolean?
                    var liveTime: Long

                    handler.post {
                        isPlaying = mediaControl?.isPlaying

                        runCatching {
                            currentLyricIndex = MainViewModelObject.syncLyricIndex.intValue

                            if (isPlaying == true) {
                                liveTime = mediaControl?.currentPosition ?: 0

                                val lrcEntries = MediaViewModelObject.lrcEntries.value

                                val nextIndex = lrcEntries.indexOfFirst { line ->
                                    line.first().first >= liveTime
                                }

                                val updateLyricIndex = fun() {
                                    try {
                                        MainViewModelObject.syncLyricIndex.intValue =
                                            currentLyricIndex

                                        val line = lrcEntries[currentLyricIndex]
                                        if (line == lastLyric) {
                                            return
                                        }

                                        lastLyric = line
                                    } catch (_: Exception) {
                                    }
                                }

                                if (nextIndex != -1) {
                                    if (nextIndex - 1 != currentLyricIndex) {
                                        currentLyricIndex = nextIndex - 1
                                    }
                                    if (currentLyricIndex != -1) {
                                        updateLyricIndex()
                                    }
                                } else if (currentLyricIndex != lrcEntries.size - 1) {
                                    currentLyricIndex = lrcEntries.size - 1
                                    if (currentLyricIndex != -1) {
                                        updateLyricIndex()
                                    }
                                }
                            }
                        }
                    }

                    handler.postDelayed(this, 70)
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            handler.post(updateLyricsRunnable)
        }
    }



    suspend fun prepare(
        music: YosMediaItem,
        thisMusicList: List<YosMediaItem>,
        position: Long = 0L,
        shuffleModeEnabled: Boolean = false,
        repeatMode: Int = REPEAT_MODE_ALL,
        play: Boolean = true
    ) {
        println("prepare $music")
        if (thisMusicList != playingMusicList.value) {

            var index = 0

            val itemList = thisMusicList.mapIndexed { thisIndex, it ->
                if (it.uri == music.uri) {
                    index = thisIndex
                }

                it.toMediaItem()
            }


            withContext(Dispatchers.Main) {
                mediaControl?.setMediaItems(itemList, index, position)
                mediaControl?.prepare()
            }

            println("prepare 调用切列表")
            if (!play && playingMusicList.value == null) {
                playingMusicList.value = thisMusicList
                //refresh(music)
                withContext(Dispatchers.Main) {
                    mediaControl?.shuffleModeEnabled = shuffleModeEnabled
                    mediaControl?.repeatMode = repeatMode
                    mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
                }
            } else {
                playingMusicList.value = thisMusicList
            }

            if (play) {
                withContext(Dispatchers.Main) {
                    mediaControl?.fadePlay()
                }
            }

            // 播放列表切换事件
            println("prepare 尝试保存播放列表")
            if (mainMusicList != null && playingMusicList.value != null) {
                println("prepare 保存播放列表")
                MusicLibrary.updatePlayList(
                    PlayListV1(
                        mainMusicList,
                        playingMusicList.value
                    )
                )
            }

        } else {
            println("prepare 调用非切列表")
            val index = thisMusicList.indexOf(music)
            withContext(Dispatchers.Main) {
                mediaControl?.seekToDefaultPosition(index)
                mediaControl?.fadePlay()
            }
        }
    }

    fun onCase(mediaItem: YosMediaItem) {
        CoroutineScope(Dispatchers.IO).launch {
            refresh(mediaItem)
        }
    }

    private var refreshJob: CompletableJob? = null

    private fun refresh(music: YosMediaItem) {
        refreshJob?.cancel()
        refreshJob = Job()

        val scope = CoroutineScope(Dispatchers.IO + refreshJob!!)

        scope.launch {
            println("prepare 刷新UI状态 $music")
            musicPlaying.value = music
            println(musicPlaying.value)
        }

        scope.launch {
            // val bitmap: MutableState<String?> = MediaViewModelObject.bitmap
            // bitmap.value = music.thumb
            MediaViewModelObject.bitmap.value = music.thumb
        }

        scope.launch {
            MainViewModelObject.syncLyricIndex.intValue = -1
        }
    }
}

class YosPlaybackService : MediaSessionService() {
    private val notificationID = 1145
    private val channelID = "YosMediaControllerChannel"

    private val shuffleMode = "shuffle_mode"
    private val repeatMode = "repeat_mode"

    companion object {
        private const val FLAG_ALWAYS_SHOW_TICKER = 0x1000000
        private const val FLAG_ONLY_UPDATE_TICKER = 0x2000000
    }

    @OptIn(UnstableApi::class)
    private fun setCustomButtons(player: ForwardingPlayer) {
        if (SettingsLibrary.NotificationEnableIcon) {
            val useSmallerIcon = SettingsLibrary.NotificationSmallerIcon

            val shuffleButtonIcon =
                if (player.shuffleModeEnabled) {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle else R.drawable.ic_shuffle
                } else {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle_off else R.drawable.ic_shuffle_off
                }
            val shuffleButton = CommandButton.Builder()
                .setIconResId(shuffleButtonIcon)
                .setDisplayName(shuffleMode)
                .setSessionCommand(SessionCommand(shuffleMode, Bundle()))
                .build()

            val repeatButtonIcon =
                when (player.repeatMode) {
                    REPEAT_MODE_ONE -> if (useSmallerIcon) R.drawable.ic_mini_repeat_one else R.drawable.ic_repeat_one
                    REPEAT_MODE_ALL -> if (useSmallerIcon) R.drawable.ic_mini_repeat else R.drawable.ic_repeat
                    else -> if (useSmallerIcon) R.drawable.ic_mini_repeat_off else R.drawable.ic_repeat_off
                }
            val repeatButton = CommandButton.Builder()
                .setIconResId(repeatButtonIcon)
                .setDisplayName(repeatMode)
                .setSessionCommand(SessionCommand(repeatMode, Bundle()))
                .build()

            mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton, repeatButton))
        } else {
            mediaSession?.setCustomLayout(emptyList())
        }
    }

    fun setCustomButtons(player: MediaController) {
        if (SettingsLibrary.NotificationEnableIcon) {
            val useSmallerIcon = SettingsLibrary.NotificationSmallerIcon

            val shuffleButtonIcon =
                if (player.shuffleModeEnabled) {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle else R.drawable.ic_shuffle
                } else {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle_off else R.drawable.ic_shuffle_off
                }
            val shuffleButton = CommandButton.Builder()
                .setIconResId(shuffleButtonIcon)
                .setDisplayName(shuffleMode)
                .setSessionCommand(SessionCommand(shuffleMode, Bundle()))
                .build()

            val repeatButtonIcon =
                when (player.repeatMode) {
                    REPEAT_MODE_ONE -> if (useSmallerIcon) R.drawable.ic_mini_repeat_one else R.drawable.ic_repeat_one
                    REPEAT_MODE_ALL -> if (useSmallerIcon) R.drawable.ic_mini_repeat else R.drawable.ic_repeat
                    else -> if (useSmallerIcon) R.drawable.ic_mini_repeat_off else R.drawable.ic_repeat_off
                }
            val repeatButton = CommandButton.Builder()
                .setIconResId(repeatButtonIcon)
                .setDisplayName(repeatMode)
                .setSessionCommand(SessionCommand(repeatMode, Bundle()))
                .build()

            mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton, repeatButton))
        } else {
            mediaSession?.setCustomLayout(emptyList())
        }
    }

    /*fun sendLyricTicker(lyric: String) {
        val notification = NotificationCompat.Builder(this, channelID).apply {
            setTicker(lyric)
            setSmallIcon(R.drawable.flamingo_icon_notification)
        }.build().also {
            it.extras.putInt("ticker_icon", R.drawable.flamingo_icon_notification)
            it.extras.putBoolean("ticker_icon_switch", true)
            it.flags = it.flags.or(FLAG_ALWAYS_SHOW_TICKER).or(FLAG_ONLY_UPDATE_TICKER)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(this).notify(notificationID, notification)
    }*/

    private var saveJob: Job? = null

    fun saveDataWithDelay() {
        saveJob?.cancel()
        saveJob = CoroutineScope(Dispatchers.IO).launch {
            delay(200)
            withContext(Dispatchers.Main) {
                saveData()
            }
        }
    }

    private fun saveData() {
        println("持久化 尝试保存播放状态")
        if (musicPlaying.value != null && mediaControl != null) {
            println("持久化 保存播放状态")
            MusicLibrary.updatePlayStatus(
                PlayStatus(
                    musicPlaying.value,
                    mediaControl?.currentPosition ?: 0,
                    mediaControl?.shuffleModeEnabled ?: false,
                    mediaControl?.repeatMode ?: REPEAT_MODE_ALL
                )
            )
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(
            this,
            YosRenderFactory(this)
                .setEnableAudioFloatOutput(
                    SettingsLibrary.AudioFloatOutput
                )
                .setEnableDecoderFallback(true)
                .setEnableAudioTrackPlaybackParams(
                    SettingsLibrary.HardwareAudioTrackPlayBackParams
                )
                .setExtensionRendererMode(
                    when (SettingsLibrary.Codec) {
                        "Auto" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                        "System" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                        else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    }
                )
        )
            .setAudioAttributes(
                audioAttributes,
                SettingsLibrary.AudioAttributes
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun play() {
                player.fadePlay()
            }

            override fun pause() {
                player.fadePause()
            }

            override fun isPlaying(): Boolean {
                return FadeExo.targetStatus != 0
            }
        }

        forwardingPlayer.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    runCatching {

                        if (tracks.isEmpty) return@runCatching

                        val lrcEntries: MutableState<List<List<Pair<Float, String>>>> =
                            MediaViewModelObject.lrcEntries
                        var lrcContent: String? = null


                        val path = player.currentMediaItem?.uri

                        println("质量分析 内置实现获取")
                        var samplingRate = 0
                        var bitrate = 0
                        var haveJOC = false

                        for (i in tracks.groups) {
                            for (j in 0 until i.length) {
                                if (!i.isTrackSelected(j)) continue
                                val trackFormat = i.getTrackFormat(j)
                                samplingRate = trackFormat.sampleRate
                                bitrate = trackFormat.bitrate / 1000
                                haveJOC =
                                    trackFormat.sampleMimeType?.contains("-joc", ignoreCase = true)
                                        ?: false
                                break
                            }
                        }

                        val thisPath = path?.path

                        val embeddedLyrics = thisPath?.let { AudioMetadataUtils.loadEmbeddedLyrics(it) }
                        val ttmlResult = (embeddedLyrics ?: thisPath?.let { AudioMetadataUtils.loadTtmlFile(it) })
                            ?.let { YosTtmlFactory().formatTtmlEntries(it) }

                        if (ttmlResult != null) {
                            MediaViewModelObject.otherSideForLines.clear()
                            MediaViewModelObject.otherSideForLines.addAll(ttmlResult.otherSideForLines)
                            MediaViewModelObject.transliterationForLines.clear()
                            MediaViewModelObject.transliterationForLines.addAll(ttmlResult.transliterationForLines)
                            lrcEntries.value = ttmlResult.entries
                        } else {
                            MediaViewModelObject.transliterationForLines.clear()
                            lrcContent = embeddedLyrics?.takeUnless { YosTtmlFactory.isTtml(it) }

                            val finalLrcContent = if (lrcContent == null) {
                                val lrcPath = "${thisPath?.substringBeforeLast(".")}.lrc"
                                println("获取歌词元数据失败，将读取：$lrcPath")
                                AudioMetadataUtils.loadLrcFile(this@YosPlaybackService, lrcPath) ?: ""
                            } else {
                                lrcContent
                            }

                            val lrcFactory = YosLrcFactory()
                            lrcEntries.value = lrcFactory.formatLrcEntries(finalLrcContent)
                        }

                        if (thisPath != null) {
                            // MediaViewModelObject.isDolby.value = thisPath.endsWith(".m4a")
                            // 改为 JOC 判断

                            if (samplingRate == 0 || bitrate == 0) {
                                val audioInfo = AudioMetadataUtils.getQualityInfos(thisPath)
                                if (samplingRate == 0) {
                                    samplingRate = audioInfo.second
                                } else {
                                    bitrate = audioInfo.first
                                }
                            }
                        }

                        MediaViewModelObject.isDolby.value = haveJOC
                        MediaViewModelObject.samplingRate.intValue = samplingRate
                        MediaViewModelObject.bitrate.intValue = bitrate

                        println("质量分析 采样率：${MediaViewModelObject.samplingRate.intValue}，比特率：${MediaViewModelObject.bitrate.intValue}")
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    /*mediaSession?.let { MediaController.sendNotification(it,context) }*/
                    mediaItem?.let {
                        yos.music.player.code.MediaController.onCase(
                            it.toYosMediaItem()
                        )
                    }

                    println("更新 $mediaItem")
                    super.onMediaItemTransition(mediaItem, reason)
                }

                /*override fun onIsPlayingChanged(isPlaying: Boolean) {
                    saveData()
                    super.onIsPlayingChanged(isPlaying)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    saveData()
                    super.onRepeatModeChanged(repeatMode)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    saveData()
                    super.onShuffleModeEnabledChanged(shuffleModeEnabled)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_BUFFERING) {
                        saveData()
                    }
                    super.onPlaybackStateChanged(playbackState)
                }*/

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    MediaViewModelObject.isPlaying.value = isPlaying
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    super.onEvents(player, events)

                    if (events.containsAny(
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_REPEAT_MODE_CHANGED,
                            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
                        )
                    ) {
                        saveDataWithDelay()
                    }
                }

            }
        )

        /*val repeatButton = CommandButton.Builder()
            .setIconResId(android.R.drawable.ic_media_rew)
            .setSessionCommand(SessionCommand(SAVE_TO_FAVORITES, Bundle()))
            .build()*/

        @Suppress("DEPRECATION")
        class YosMediaSessionCallback : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands =
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(shuffleMode, Bundle.EMPTY))
                        .add(SessionCommand(repeatMode, Bundle.EMPTY))
                        .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == shuffleMode) {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    setCustomButtons(forwardingPlayer)
                } else if (customCommand.customAction == repeatMode) {
                    when (player.repeatMode) {
                        REPEAT_MODE_OFF -> {
                            player.repeatMode = REPEAT_MODE_ALL
                        }

                        REPEAT_MODE_ALL -> {
                            player.repeatMode = REPEAT_MODE_ONE
                        }

                        else -> {
                            player.repeatMode = REPEAT_MODE_OFF
                        }
                    }
                    setCustomButtons(forwardingPlayer)
                }
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_SUCCESS)
                )
            }
            /*override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            player.fadePlay()
                        }

                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player.fadePause()
                        }

                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            player.seekToNextMediaItem()
                        }

                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            player.seekToPreviousMediaItem()
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }*/
        }

        mediaSession =
            MediaSession
                .Builder(this, forwardingPlayer)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .setShowPlayButtonIfPlaybackIsSuppressed(true)
                .setCallback(YosMediaSessionCallback())
                .build()
        /*
                val mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java)
                val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                mediaButtonIntent.component = mediaButtonReceiver
                val pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                mediaSession.setMediaButtonReceiver(pendingIntent)
        */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Pear Media Control"
            val descriptionText = "Pear Media Control Notification Channel"
            val importance = NotificationManager.IMPORTANCE_NONE
            val channel = NotificationChannel(channelID, name, importance).apply {
                description = descriptionText
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
                setSound(null, null)
            }
            val notificationManager: NotificationManager =
                ContextCompat.getSystemService(
                    this,
                    NotificationManager::class.java
                ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationProvider =
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(notificationID)
                .setChannelId(channelID)
                .build()

        /*DefaultMediaNotificationProvider(
            this,
            {
                notificationID
            },
            channelID,
            notificationID
        )*/

        notificationProvider.setSmallIcon(R.drawable.flamingo_icon_notification)

        this.setMediaNotificationProvider(notificationProvider)

        setCustomButtons(forwardingPlayer)

        onServiceRunning()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession
}
