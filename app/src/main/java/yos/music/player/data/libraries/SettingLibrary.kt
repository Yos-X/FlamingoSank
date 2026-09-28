package yos.music.player.data.libraries

import androidx.compose.runtime.Stable
import com.funny.data_saver.core.mutableDataSaverStateOf
import yos.music.player.data.SettingsSaver

@Stable
object SettingsLibrary {

    /**
     * 是否显示音量条
     */
    @Stable
    var NowPlayingShowVolumeBar by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_nowplaying_show_volume_bar",
        initialValue = true
    )

    /**
     * 应用主题
     */
    @Stable
    var CustomTheme by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_theme",
        initialValue = "Auto"
    )

    /**
     * 是否已设置过屏幕圆角大小
     */
    @Stable
    var ScreenCornerSet by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_corner_set",
        initialValue = false
    )

    /**
     * 屏幕圆角大小
     */
    @Stable
    var ScreenCorner by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_corner",
        initialValue = "30"
    )

    /**
     * 歌曲排序
     */
    @Stable
    var SongSort by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "yos_player_song_sort",
        initialValue = SongSortEnum.MUSIC_TITLE.ordinal
    )

    @Stable
    enum class SongSortEnum {
        MUSIC_TITLE, MUSIC_DURATION, ARTIST_NAME, MODIFIED_DATE
    }

    /**
     * 启用降序
     */
    @Stable
    var EnableDescending by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "yos_player_enable_descending",
        initialValue = false
    )

    /**
     * 歌词界面 - 翻译
     */
    @Stable
    var NowPlayingTranslation by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "now_playing_translation",
        initialValue = true
    )

    /**
     * 每次启动时刷新媒体库
     */
    @Stable
    var RefreshEveryTime by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_library_refresh_everytime",
        initialValue = false
    )

    /**
     * 歌词字体字重
     */
    @Stable
    var LyricFontWeight by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_font_weight",
        initialValue = "ExtraBold"
    )

    /**
     * 歌词平衡行模式
     */
    @Stable
    var LyricLineBalance by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_line_balance",
        initialValue = false
    )

    /**
     * 歌词模糊效果
     */
    @Stable
    var LyricBlurEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_blur_effect",
        initialValue = false
    )

    /**
     * 播放界面背景动态效果
     */
    @Stable
    var NowplayingBackgroundEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_nowplaying_background_effect",
        initialValue = false
    )

    /**
     * 界面工具栏模糊效果
     */
    @Stable
    var BarBlurEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_blur_effect",
        initialValue = false
    )

    /**
     * 媒体通知-额外的媒体图标
     */
    @Stable
    var NotificationEnableIcon by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_notification_enable_icon",
        initialValue = true
    )

    /**
     * 媒体通知-小一号图标
     */
    @Stable
    var NotificationSmallerIcon by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_notification_smaller_icon",
        initialValue = false
    )

    /**
     * 渐入渐出播放
     */
    @Stable
    var FadePlay by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_fade_in_out",
        initialValue = true
    )

    /**
     * 播放历史
     */
    @Stable
    var ListenHistory by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_play_history",
        initialValue = true
    )

    /**
     * ExoPlayer行为 - 音频属性
     */
    @Stable
    var AudioAttributes by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_audio_attributes",
        initialValue = true
    )

    /**
     * ExoPlayer解码 - 编解码器
     */
    @Stable
    var Codec by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_codec",
        initialValue = "Auto"
    )

    /**
     * ExoPlayer解码 - 硬件音频轨道播放参数
     */
    @Stable
    var HardwareAudioTrackPlayBackParams by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_hardware_audio_track_playback_params",
        initialValue = false
    )

    /**
     * ExoPlayer解码 - 音频浮点输出
     */
    @Stable
    var AudioFloatOutput by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_audio_float_output",
        initialValue = false
    )

    /**
     * 排除一分钟以内的歌曲
     */
    @Stable
    var EnableExcludeSongsUnderOneMinute by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_library_enable_exclude_songs_under_one_minute",
        initialValue = true
    )
}
