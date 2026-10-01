package yos.music.player.ui.widgets.effects

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flaviofaria.kenburnsview.KenBurnsView
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yos.music.player.code.utils.others.BitmapResolver
import yos.music.player.data.libraries.SettingsLibrary.NowplayingBackgroundEffect
import yos.music.player.ui.pages.NowPlayingPage
import yos.music.player.ui.widgets.basic.YosWrapper

@Stable
private enum class Option {
    Set,
    Pause,
    Resume,
    Init
}

@Composable
fun YosFloatingLight(
    modifier: Modifier,
    album: () -> Uri?,
    isPlaying: () -> Boolean,
    nowPage: () -> String,
    showMiniPlayer: () -> Boolean
) {
    val drawable = remember(album) {
        mutableStateOf<Drawable?>(null)
    }

    println("封面：" + drawable.value)

    val context = LocalContext.current
    val imageLoader = ImageLoader(context)
    YosWrapper {
        LaunchedEffect(album()) {
            if (album() == null) return@LaunchedEffect
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(album())
                    .build()
                val thisBitmap = imageLoader.execute(request).drawable?.toBitmap()?.run {
                    BitmapResolver.bitmapCompress(this)
                }
                if (thisBitmap != null) {
                    drawable.value = imageResolve(
                        thisBitmap
                    ).toDrawable(context.resources)
                    thisBitmap.recycle()
                }
                imageLoader.shutdown()
            }
        }
    }

    YosWrapper {
        val lossEffect = remember("YosFloatingLight_lossEffect") {
            derivedStateOf {
                nowPage() != NowPlayingPage.Lyric
            }
        }

        val useBackground = remember("YosFloatingLight_useBackground") {
            derivedStateOf {
                album() == null
            }
        }

        if (NowplayingBackgroundEffect) {
            val lastOption = remember("YosFloatingLight_lastOption") {
                mutableStateOf(Option.Init.name)
            }
            YosWrapper {
                val lifecycleState =
                    LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
                val active = lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED)&&!showMiniPlayer()
                AndroidView(factory = {
                    KenBurnsView(it).apply {
                        setTransitionGenerator(
                            RandomTransitionGenerator(
                                12000,
                                AccelerateDecelerateInterpolator()
                            )
                        )
                    }
                }, modifier = modifier.drawWithCache {
                    onDrawBehind {
                        if (useBackground.value) {
                        drawRect(Color.Black)
                            }
                    }
                }) {
                    if (drawable.value != null) {
                        if (it.drawable != drawable.value) {
                            val thisOptionType = Option.Set.name
                            if (lastOption.value == thisOptionType) return@AndroidView
                            println("流光：设置背景")
                            it.setImageDrawable(drawable.value!!)
                            lastOption.value = thisOptionType
                        } else if (!isPlaying() || !active) {
                            val thisOptionType = Option.Pause.name
                            if (lastOption.value == thisOptionType) return@AndroidView
                            println("流光：暂停")
                            it.pause()
                            lastOption.value = thisOptionType
                        } else {
                            val thisOptionType = Option.Resume.name
                            if (lastOption.value == thisOptionType) return@AndroidView
                            println("流光：恢复")
                            it.resume()
                            lastOption.value = thisOptionType
                        }
                    }
                }
            }
        } else {
            YosWrapper {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(data = drawable.value)
                        .crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithCache {
                            onDrawBehind {
                                if (useBackground.value) {
                                    drawRect(Color.Black)
                                }
                            }
                        }
                )
            }
        }

        YosWrapper {
            val alpha = animateFloatAsState(
                targetValue = if (lossEffect.value) 0.618f else 0f, animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
            AsyncImage(
                model = ImageRequest.Builder(context).data(data = drawable.value)
                    .crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        this.alpha = alpha.value
                    },
                colorFilter = ColorFilter.tint(Color(0x33000000), BlendMode.Overlay)
            )
        }
    }
}

fun imageResolve(image: Bitmap, moreLight: Boolean = false): Bitmap {
    var resizedBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
    resizedBitmap.applyCanvas {
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.isDither = true

        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(1.8f)

        paint.colorFilter = ColorMatrixColorFilter(saturationMatrix)
        drawBitmap(resizedBitmap, 0f, 0f, paint)

        if (moreLight) {
            drawColor((0x1AFFFFFF).toInt())
            drawColor((0xFFFFFFFF).toInt(), PorterDuff.Mode.OVERLAY)
            drawColor((0x52FFFFFF).toInt())
            drawColor((0xBFFFFFFF).toInt(), PorterDuff.Mode.OVERLAY)
        } else {
            drawColor((0x33000000).toInt(), PorterDuff.Mode.OVERLAY)
            drawColor((0x59000000).toInt())
        }
    }
    resizedBitmap = Toolkit.blur(resizedBitmap, 25)
    return resizedBitmap
}