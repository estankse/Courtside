package cn.courtside.client

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import kotlinx.coroutines.delay

internal data class RefereeEntryPhoto(val image: Int, val focus: Alignment)

private val entryPhotos = listOf(
    RefereeEntryPhoto(R.drawable.referee_workbench_victory, Alignment.TopCenter),
    RefereeEntryPhoto(R.drawable.referee_detail_lin_dan, Alignment.Center),
    RefereeEntryPhoto(R.drawable.referee_detail_lee, Alignment.CenterEnd),
    RefereeEntryPhoto(R.drawable.referee_schedule_chen_long, Alignment.Center),
    RefereeEntryPhoto(R.drawable.referee_records_axelsen, Alignment.Center),
    RefereeEntryPhoto(R.drawable.referee_card_momota, Alignment.Center),
    RefereeEntryPhoto(R.drawable.referee_card_ginting, Alignment.Center),
)

private val LocalRefereeEntryPhoto = staticCompositionLocalOf { entryPhotos.first() }

private fun nextEntryPhoto(context: Context): Int {
    val preferences = context.getSharedPreferences("courtside_visuals", Context.MODE_PRIVATE)
    val previous = preferences.getInt("entry_photo", -1)
    val next = if (previous in entryPhotos.indices) {
        // Random without an immediate repeat, rather than a new draw on every recomposition.
        Random.nextInt(entryPhotos.size - 1).let { if (it >= previous) it + 1 else it }
    } else Random.nextInt(entryPhotos.size)
    preferences.edit().putInt("entry_photo", next).apply()
    return next
}

internal const val REFEREE_LAUNCH_DURATION_MILLIS = 3500L

/** Activity-only entry: loading continues underneath, with time to see the first rendered photo. */
@Composable
fun RefereeLaunchHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val photoIndex = rememberSaveable { nextEntryPhoto(context) }
    var entering by rememberSaveable { mutableStateOf(true) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(entering) {
        if (entering) {
            if (startedAt == 0L) {
                // Let a frame draw before starting the visible display duration.
                withFrameNanos { }
                withFrameNanos { }
                startedAt = SystemClock.elapsedRealtime()
            }
            delay((REFEREE_LAUNCH_DURATION_MILLIS - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L))
            entering = false
        }
    }
    CompositionLocalProvider(LocalRefereeEntryPhoto provides entryPhotos[photoIndex]) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            content()
            AnimatedVisibility(
                visible = entering,
                exit = fadeOut(tween(220)),
                modifier = Modifier.matchParentSize(),
            ) {
                RefereeLaunchCover { entering = false }
            }
        }
    }
}

/** The selected badge artwork is shared with the launcher and the system splash icon. */
@Composable
internal fun CourtsideBrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.referee_brand_badge),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
internal fun RefereeEntryArtwork(modifier: Modifier = Modifier, subdued: Boolean = false) {
    val photo = LocalRefereeEntryPhoto.current
    Box(modifier.background(Ink)) {
        Image(
            painterResource(photo.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = photo.focus,
            modifier = Modifier.matchParentSize(),
            alpha = if (subdued) .52f else .92f,
        )
        Box(Modifier.matchParentSize().background(
            Brush.verticalGradient(
                0f to Ink.copy(alpha = if (subdued) .8f else .22f),
                .28f to Ink.copy(alpha = if (subdued) .25f else .04f),
                .6f to Ink.copy(alpha = if (subdued) .65f else .42f),
                1f to Ink,
            )
        ))
        Box(Modifier.matchParentSize().background(
            Brush.horizontalGradient(
                0f to Ink.copy(alpha = .38f),
                .64f to Color.Transparent,
                1f to Ink.copy(alpha = .18f),
            )
        ))
    }
}

@Composable
private fun RefereeLaunchCover(onContinue: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink).clickable(
        onClickLabel = "进入裁判端", role = Role.Button, onClick = onContinue,
    )) {
        val wide = maxWidth > maxHeight
        RefereeEntryArtwork(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(
                horizontal = 32.dp, vertical = if (wide) 18.dp else 30.dp,
            ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(Lime, RoundedCornerShape(50)))
                Spacer(Modifier.width(8.dp))
                Text("COURTSIDE  /  REFEREE", color = Paper, fontSize = 10.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            }
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    CourtsideBrandMark(Modifier.size(84.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("羽序", fontFamily = PlayerScript, fontSize = 48.sp, color = Paper)
                        Text("专注球场 · 掌握每一分", color = Paper, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold)
                        Text("轻触进入", color = Paper.copy(alpha = .65f), fontSize = 12.sp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CourtsideBrandMark(Modifier.size(88.dp))
                    Text("羽序", fontFamily = PlayerScript, fontSize = 58.sp, color = Paper)
                    Box(Modifier.width(44.dp).height(3.dp).background(Lime))
                    Text("专注球场\n掌握每一分", fontWeight = FontWeight.Black,
                        fontSize = 30.sp, lineHeight = 39.sp, color = Paper)
                    Text("轻触进入", color = Paper.copy(alpha = .65f), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}
