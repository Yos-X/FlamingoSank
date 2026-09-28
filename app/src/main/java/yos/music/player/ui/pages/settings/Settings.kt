package yos.music.player.ui.pages.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title

@Composable
fun Settings(navController: NavController) =
    SettingBackground {
        val context = LocalContext.current
        Title(title = stringResource(id = R.string.page_settings_title),
            onBack = {
                navController.popBackStack()
            },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {
                        // GroupSpacerMedium()
                        ListHeader(stringResource(id = R.string.page_library_title))
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_library_refresh_everytime),
                                onClick = {
                                    SettingsLibrary.RefreshEveryTime =
                                        !SettingsLibrary.RefreshEveryTime
                                },
                                checkedLambda = { SettingsLibrary.RefreshEveryTime }
                            )

                            Divider()
                            LabelItem(title = stringResource(id = R.string.settings_library_overview)) {
                                navController.toUI(UI.Settings.LibraryOverview)
                            }
                        }

                        GroupSpacerMedium()
                        RoundColumn {
                            val scope = rememberCoroutineScope()
                            LabelItem(
                                title = stringResource(id = R.string.settings_library_refresh_now),
                                //desc = stringResource(id = R.string.settings_library_refresh_now_desc)
                                superLink = true
                            ) {
                                scope.launch(Dispatchers.Main) {
                                    var toast = Toast.makeText(
                                        context,
                                        R.string.tip_scanning,
                                        Toast.LENGTH_SHORT
                                    )
                                    toast.show()
                                    withContext(Dispatchers.IO) {
                                        MusicLibrary.scanMedia(context)
                                    }
                                    toast.cancel()
                                    val size = MediaController.mainMusicList.size
                                    if (size == 0) {
                                        toast = Toast.makeText(
                                            context,
                                            R.string.tip_no_song,
                                            Toast.LENGTH_SHORT
                                        )
                                    } else {
                                        val msg =
                                            context.getString(R.string.tip_scan_finished, size)
                                        toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                                    }
                                    toast.show()
                                }
                            }
                        }
                        ListHeader(content = stringResource(id = R.string.settings_library_refresh_now_desc))

                        GroupSpacer()
                        ListHeader(stringResource(id = R.string.settings_performance))
                        RoundColumn {
                            LabelItem(title = stringResource(id = R.string.settings_performance_lyric_title)) {
                                navController.toUI(UI.Settings.LyricSetting)
                            }
                            Divider()
                            LabelItem(title = stringResource(id = R.string.settings_performance_ui_title)) {
                                navController.toUI(UI.Settings.UserInterfaceSetting)
                            }
                            Divider()
                            LabelItem(title = stringResource(id = R.string.settings_performance_notification_title)) {
                                navController.toUI(UI.Settings.NotificationSetting)
                            }
                        }

                        GroupSpacer()
                        ListHeader(stringResource(id = R.string.settings_audio))
                        RoundColumn {
                            LabelItem(title = stringResource(id = R.string.settings_audio_exoplayer)) {
                                navController.toUI(UI.Settings.ExoplayerSetting)
                            }
                            Divider()
                            SwitchItem(
                                title = stringResource(id = R.string.settings_audio_fade_in_out),
                                // desc = stringResource(id = R.string.settings_audio_fade_in_out_desc),
                                onClick = { },
                                checkedLambda = { SettingsLibrary.FadePlay }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_audio_fade_in_out_desc))

                        GroupSpacer()
                        ListHeader(stringResource(id = R.string.settings_play))
                        RoundColumn {
                            SwitchItem(
                                title = stringResource(id = R.string.settings_play_history),
                                // desc = stringResource(id = R.string.settings_play_history_desc),
                                onClick = { },
                                checkedLambda = { SettingsLibrary.ListenHistory }
                            )
                        }
                        ListHeader(content = stringResource(id = R.string.settings_play_history_desc))

                        GroupSpacer()
                        ListHeader(stringResource(id = R.string.settings_others))
                        RoundColumn {
                            LabelItem(
                                title = stringResource(id = R.string.settings_others_about),
                                // desc = stringResource(id = R.string.settings_others_about_desc)
                            ) {
                                navController.toUI(UI.Settings.About)
                            }
                        }
                    }
                }

                /*item("blank") {
                    Spacer(modifier = Modifier.height(15.dp))
                }*/
            })
    }


fun safeStartActivity(context: Context, intent: Intent, options: Bundle?) {
    if (intent.resolveActivity(context.packageManager) != null) {
        ContextCompat.startActivity(context, intent, options)
    } else {
        Toast.makeText(
            context,
            context.getString(R.string.tip_intent_resolve_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun startWeb(url: String, context: Context) {
    try {
        val uri: Uri =
            Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        safeStartActivity(context, intent, null)
    } catch (_: Exception) {
    }
}
