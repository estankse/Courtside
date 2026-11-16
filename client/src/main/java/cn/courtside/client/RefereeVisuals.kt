package cn.courtside.client

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Referee-only photography; the gradients keep match data and controls in the foreground. */
internal enum class RefereeScene(val image: Int) {
    Login(R.drawable.referee_rivals),
    Workbench(R.drawable.referee_workbench_victory),
    Interval(R.drawable.referee_interval_arena),
    GameBreak(R.drawable.referee_game_break_fu),
}

@Composable
internal fun RefereeHeroArt(
    modifier: Modifier = Modifier,
    scene: RefereeScene = RefereeScene.Workbench,
) {
    val widePhoto = scene == RefereeScene.Login || scene == RefereeScene.Interval
    Box(modifier.background(Ink)) {
        Image(
            painterResource(scene.image),
            contentDescription = null,
            modifier =
                Modifier.fillMaxHeight()
                    .fillMaxWidth(if (widePhoto) 1f else .78f)
                    .align(Alignment.CenterEnd),
            contentScale = ContentScale.Crop,
            alignment = if (widePhoto) Alignment.Center else Alignment.TopCenter,
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Ink.copy(alpha = .98f),
                        .36f to Ink.copy(alpha = .92f),
                        .64f to Ink.copy(alpha = .57f),
                        1f to Ink.copy(alpha = .06f),
                    )
                )
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Ink,
                        .22f to Ink.copy(alpha = .55f),
                        .5f to Color.Transparent,
                        1f to Ink.copy(alpha = .96f),
                    )
                )
        )
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            // Stadium light and restrained speed lines echo the original blue/red match graphics.
            drawRect(
                Brush.radialGradient(
                    listOf(Blue.copy(alpha = .16f), Color.Transparent),
                    center = Offset(w, 0f),
                    radius = w * .75f,
                )
            )
            val corner =
                Path().apply {
                    moveTo(w * .89f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h * .27f)
                    close()
                }
            drawPath(corner, Lime.copy(alpha = .11f))
            repeat(3) { i ->
                val x = w * .85f + i * 8.dp.toPx()
                drawLine(
                    Lime.copy(alpha = .7f),
                    Offset(x, 14.dp.toPx()),
                    Offset(x - 7.dp.toPx(), 29.dp.toPx()),
                    2.dp.toPx(),
                )
            }
            drawLine(
                Blue.copy(alpha = .3f),
                Offset(w * .58f, h),
                Offset(w * .9f, h * .48f),
                1.dp.toPx(),
            )
            drawLine(Rose.copy(alpha = .32f), Offset(w * .73f, h), Offset(w, h * .57f), 1.dp.toPx())
        }
        if (scene == RefereeScene.Login) {
            Text(
                "LIN DAN  /  LEE CHONG WEI",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 4.dp),
                color = Paper.copy(alpha = .56f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
internal fun RefereeRecordsHeading() {
    RefereePageHeading(
        "执裁记录",
        "从第一球到赛后确认，都有迹可循。",
        R.drawable.referee_records_axelsen,
        Alignment.TopCenter,
    )
}

@Composable
internal fun RefereeScheduleHeading() {
    RefereePageHeading(
        "我的赛程",
        "核对场地和时间，接受后准时到场。",
        R.drawable.referee_schedule_chen_long,
        Alignment.Center,
    )
}

@Composable
private fun RefereePageHeading(title: String, subtitle: String, image: Int, focus: Alignment) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize()) {
            Image(
                painterResource(image),
                contentDescription = null,
                modifier = Modifier.fillMaxHeight().width(140.dp).align(Alignment.CenterEnd),
                contentScale = ContentScale.Crop,
                alignment = focus,
                alpha = .55f,
            )
        }
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.horizontalGradient(0f to Ink, .55f to Ink, 1f to Ink.copy(alpha = .1f))
                )
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Ink,
                        .2f to Color.Transparent,
                        .72f to Color.Transparent,
                        1f to Ink,
                    )
                )
        )
        Column {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Soft, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun RefereeRecapArt(modifier: Modifier = Modifier) {
    Box(modifier.background(Ink)) {
        Image(
            painterResource(R.drawable.referee_recap_doubles),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            alpha = .35f,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Ink.copy(alpha = .2f),
                        .45f to Ink.copy(alpha = .55f),
                        1f to PanelColor.copy(alpha = .95f),
                    )
                )
        )
    }
}

/** Decorative layers carry no gestures or labels; the whole score card remains the tap target. */
@Composable
internal fun RefereeScoreArt(
    side: Int,
    bright: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val accent = if (side == 0) Blue else Rose
    val surface = if (bright) Color.White else PanelColor
    Box(modifier) {
        Image(
            painterResource(
                if (side == 0) R.drawable.referee_lin_dan else R.drawable.referee_lee_chong_wei
            ),
            contentDescription = null,
            modifier =
                Modifier.fillMaxWidth(if (landscape) .68f else 1f)
                    .fillMaxHeight(if (landscape) 1f else .72f)
                    .align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            alpha = if (bright) .14f else if (landscape) .52f else if (compact) .28f else .42f,
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    if (landscape)
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = .18f),
                            .35f to surface.copy(alpha = .28f),
                            .72f to surface.copy(alpha = .4f),
                            1f to surface.copy(alpha = .95f),
                        )
                    else
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = .15f),
                            .28f to surface.copy(alpha = .95f),
                            .56f to surface.copy(alpha = if (bright) .65f else .3f),
                            .8f to surface.copy(alpha = .6f),
                            1f to surface.copy(alpha = .96f),
                        )
                )
        )
        if (landscape)
            Box(
                Modifier.matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to surface.copy(alpha = .94f),
                            .4f to surface.copy(alpha = .86f),
                            .7f to surface.copy(alpha = .25f),
                            1f to surface.copy(alpha = .18f),
                        )
                    )
            )
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val color = if (bright) accent.copy(alpha = .3f) else accent.copy(alpha = .22f)
            repeat(3) { i ->
                val x = w - (18 + i * 7).dp.toPx()
                drawLine(
                    color,
                    Offset(x, 10.dp.toPx()),
                    Offset(x - 5.dp.toPx(), 20.dp.toPx()),
                    1.5.dp.toPx(),
                )
            }
            drawLine(color, Offset(w * .58f, h), Offset(w, h * .79f), 1.dp.toPx())
            drawLine(
                color.copy(alpha = color.alpha * .5f),
                Offset(w * .35f, h),
                Offset(w, h * .67f),
                1.dp.toPx(),
            )
        }
    }
}
