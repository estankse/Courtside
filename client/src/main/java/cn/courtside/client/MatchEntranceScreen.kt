package cn.courtside.client

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import cn.courtside.shared.Models.Match
import kotlin.math.*

/** A presentation of an already-persisted session; it never starts a game or changes its score. */
@Composable
internal fun MatchEntranceScreen(
    match: Match,
    entrance: MatchEntranceState,
    onFinished: (String) -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion =
        remember(context, entrance.id) {
            runCatching {
                    Settings.Global.getFloat(
                        context.contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f,
                    ) <= 0f
                }
                .getOrDefault(false)
        }
    var progress by
        remember(entrance.id) {
            mutableFloatStateOf(
                ((SystemClock.elapsedRealtime() - entrance.startedAtMillis).toFloat() /
                        entrance.durationMillis)
                    .coerceIn(0f, 1f)
            )
        }
    LaunchedEffect(entrance.id, reducedMotion) {
        if (reducedMotion) {
            onFinished(entrance.id)
        } else {
            while (true) {
                // Elapsed time belongs to the ViewModel, so rotation/backgrounding cannot replay
                // it.
                progress =
                    ((SystemClock.elapsedRealtime() - entrance.startedAtMillis).toFloat() /
                            entrance.durationMillis)
                        .coerceIn(0f, 1f)
                if (progress >= 1f) {
                    onFinished(entrance.id)
                    break
                }
                withFrameNanos {}
            }
        }
    }
    if (reducedMotion) {
        Box(Modifier.fillMaxSize().background(Ink))
        return
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Ink).semantics { contentDescription = "对局入场动画" }
    ) {
        val wide = maxWidth > maxHeight
        val reveal = entrancePhase(progress, .03f, .3f)
        val names = entrancePhase(progress, .16f, .42f)
        val impact = entrancePhase(progress, .26f, .44f)
        val exit = 1f - entrancePhase(progress, .9f, 1f)
        EntranceAtmosphere(progress, Modifier.matchParentSize())
        val panelModifier =
            Modifier.fillMaxSize()
                .padding(
                    horizontal = if (wide) 24.dp else 16.dp,
                    vertical = if (wide) 64.dp else 82.dp,
                )
        if (wide) {
            Row(panelModifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EntranceSide(
                    match.sideA,
                    match.unitA,
                    0,
                    wide,
                    reveal,
                    names,
                    exit,
                    Modifier.weight(1f).fillMaxHeight(),
                )
                EntranceSide(
                    match.sideB,
                    match.unitB,
                    1,
                    wide,
                    reveal,
                    names,
                    exit,
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            Column(panelModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EntranceSide(
                    match.sideA,
                    match.unitA,
                    0,
                    wide,
                    reveal,
                    names,
                    exit,
                    Modifier.weight(1f).fillMaxWidth(),
                )
                EntranceSide(
                    match.sideB,
                    match.unitB,
                    1,
                    wide,
                    reveal,
                    names,
                    exit,
                    Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
        Text(
            "VS",
            color = Lime,
            fontSize = if (wide) 80.sp else 68.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = (-5).sp,
            style = TextStyle(shadow = Shadow(Lime.copy(alpha = .55f), blurRadius = 35f)),
            modifier =
                Modifier.align(Alignment.Center)
                    .graphicsLayer {
                        alpha = impact * exit
                        val bump = sin(impact * PI).toFloat() * .18f
                        scaleX = .7f + .3f * impact + bump
                        scaleY = scaleX
                        rotationZ = -9f
                    }
                    .background(Ink.copy(alpha = .84f), CutCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("COURTSIDE / MATCH UP", Lime)
                Text(
                    match.round,
                    color = Paper,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (entrance.resumed) "继续对局" else "选手登场",
                color = Soft,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (progress < .65f) "MATCH UP / 双方就位"
                        else if (entrance.resumed) "COURT READY / 继续计分" else "COURT READY / 准备开赛",
                        color = Lime,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        "${match.court}  ·  ${match.rules.target} 分制  ·  ${match.rules.gamesToWin*2-1} 局 ${match.rules.gamesToWin} 胜",
                        color = Soft,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = { onFinished(entrance.id) },
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics { contentDescription = "跳过入场动画" },
                ) {
                    Text("跳过 · 直接计分", fontSize = 11.sp, color = Paper)
                }
            }
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                drawRect(Edge)
                drawRect(Lime, size = size.copy(width = size.width * progress))
            }
        }
    }
}

private fun entrancePhase(value: Float, from: Float, to: Float): Float =
    FastOutSlowInEasing.transform(((value - from) / (to - from)).coerceIn(0f, 1f))

@Composable
private fun EntranceSide(
    name: String,
    unit: String,
    side: Int,
    wide: Boolean,
    reveal: Float,
    names: Float,
    exit: Float,
    modifier: Modifier,
) {
    val accent = if (side == 0) Blue else Rose
    val edge = if (side == 0) -1f else 1f
    val rightAligned = wide && side == 1
    Box(
        modifier
            .graphicsLayer {
                translationX = edge * (1f - reveal) * size.width
                alpha = reveal * exit
            }
            .clip(
                CutCornerShape(
                    topEnd = if (side == 0) 36.dp else 12.dp,
                    bottomStart = if (side == 0) 12.dp else 36.dp,
                )
            )
            .background(accent.copy(alpha = .12f))
            .border(
                1.dp,
                accent.copy(alpha = .6f),
                CutCornerShape(
                    topEnd = if (side == 0) 36.dp else 12.dp,
                    bottomStart = if (side == 0) 12.dp else 36.dp,
                ),
            )
    ) {
        Image(
            painterResource(
                if (side == 0) R.drawable.referee_entrance_lin_dan
                else R.drawable.referee_entrance_lee
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            alpha = .65f,
            modifier =
                Modifier.fillMaxHeight()
                    .fillMaxWidth(if (wide) .9f else .72f)
                    .align(if (rightAligned) Alignment.CenterStart else Alignment.CenterEnd)
                    .graphicsLayer {
                        translationX = (1f - reveal) * 28.dp.toPx()
                        scaleX = 1.08f - .08f * reveal
                        scaleY = scaleX
                    },
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    if (rightAligned)
                        Brush.horizontalGradient(
                            0f to Ink.copy(alpha = .12f),
                            .28f to Ink.copy(alpha = .25f),
                            .68f to Ink.copy(alpha = .8f),
                            1f to Ink.copy(alpha = .96f),
                        )
                    else
                        Brush.horizontalGradient(
                            0f to Ink.copy(alpha = .96f),
                            .32f to Ink.copy(alpha = .8f),
                            .72f to Ink.copy(alpha = .25f),
                            1f to Ink.copy(alpha = .12f),
                        )
                )
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .88f)))
                )
        )
        Canvas(Modifier.matchParentSize()) {
            drawLine(
                accent.copy(alpha = .5f),
                Offset(size.width * .4f, size.height),
                Offset(size.width, size.height * .58f),
                1.dp.toPx(),
            )
            drawLine(
                accent.copy(alpha = .22f),
                Offset(size.width * .2f, size.height),
                Offset(size.width, size.height * .4f),
                1.dp.toPx(),
            )
            drawRect(
                accent,
                size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height * .26f),
                topLeft = Offset(0f, size.height * .37f),
            )
        }
        val nameSize =
            when {
                name.length > 12 -> if (wide) 29.sp else 27.sp
                name.length > 7 -> if (wide) 38.sp else 33.sp
                else -> if (wide) 52.sp else 44.sp
            }
        Column(
            Modifier.align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = if (wide) 32.dp else 24.dp)
                .graphicsLayer {
                    alpha = names
                    translationX = edge * (1f - names) * 28.dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (rightAligned) Alignment.End else Alignment.Start,
        ) {
            Eyebrow(if (side == 0) "BLUE SIDE / 蓝方" else "RED SIDE / 红方", accent)
            Text(
                name,
                fontFamily = PlayerScript,
                color = Paper,
                fontSize = nameSize,
                lineHeight = nameSize * 1.2f,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (rightAligned) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(if (wide) .82f else .9f),
            )
            Text(
                unit,
                color = accent,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EntranceAtmosphere(progress: Float, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                listOf(Blue.copy(alpha = .17f), Color.Transparent),
                Offset(w * .2f, h * .35f),
                w * .7f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Rose.copy(alpha = .15f), Color.Transparent),
                Offset(w * .82f, h * .7f),
                w * .65f,
            )
        )
        repeat(14) { i ->
            val y = (i / 14f + progress * .12f) % 1f * h
            drawLine(Edge.copy(alpha = .35f), Offset(0f, y), Offset(w, y - h * .15f), 1.dp.toPx())
        }
        repeat(20) { i ->
            val x = ((i * 73 % 997) / 997f + progress * (.05f + (i % 4) * .025f)) % 1f * w
            val y = (i * 37 % 101) / 101f * h
            drawCircle(
                if (i % 3 == 0) Lime.copy(alpha = .5f) else Paper.copy(alpha = .22f),
                if (i % 3 == 0) 1.6.dp.toPx() else .8.dp.toPx(),
                Offset(x, y),
            )
        }
        val impact = (1f - abs((progress - .35f) / .1f)).coerceIn(0f, 1f)
        drawCircle(
            Lime.copy(alpha = impact * .18f),
            min(w, h) * (.2f + .45f * impact),
            Offset(w * .5f, h * .5f),
            style = Stroke(2.dp.toPx()),
        )
        drawLine(
            Lime.copy(alpha = .08f + impact * .45f),
            Offset(w * .62f, 0f),
            Offset(w * .38f, h),
            2.dp.toPx(),
        )
    }
}
