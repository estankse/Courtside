package cn.courtside.client

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import cn.courtside.shared.Models.*
import cn.courtside.shared.Scoring
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

val Ink = Color(0xFF080F18)
val PanelColor = Color(0xFF121E2B)
val Soft = Color(0xFF93A5B8)
val Lime = Color(0xFFDDFB83)
val Blue = Color(0xFF8FC8FF)
val Rose = Color(0xFFFF96AD)
val Edge = Color(0xFF273647)
val Paper = Color(0xFFF1F6FA)
val PlayerScript = FontFamily(Font(R.font.player_script))

@Composable
fun CourtsideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Lime,
                onPrimary = Ink,
                secondary = Blue,
                tertiary = Rose,
                background = Ink,
                surface = PanelColor,
                surfaceVariant = Edge,
                onSurface = Paper,
                onSurfaceVariant = Soft,
                outline = Edge,
                error = Rose,
            ),
        content = { CompositionLocalProvider(LocalContentColor provides Paper, content = content) },
    )
}

@Composable
fun Eyebrow(text: String, color: Color = Soft) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.8.sp,
    )
}

@Composable
fun SectionTitle(text: String, extra: String = "", action: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(17.dp).background(Lime))
        Spacer(Modifier.width(9.dp))
        Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (extra.isNotBlank())
            TextButton(onClick = { action?.invoke() }) {
                Text(extra, color = Lime, fontSize = 12.sp)
            }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(PanelColor)
            .border(1.dp, Edge.copy(alpha = .7f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 50.dp),
        shape = CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, Modifier.size(22.dp)) }
}

@Composable
fun StatusPill(status: String) {
    val (text, color) =
        when (status) {
            "DRAFT" -> "草稿" to Soft
            "PUBLISHED" -> "待接受" to Blue
            "ACCEPTED" -> "已接受" to Lime
            "IN_PROGRESS" -> "进行中" to Lime
            "PENDING_REVIEW" -> "待核验" to Rose
            "FINISHED" -> "已完成" to Soft
            "CANCELLED" -> "已取消" to Soft
            else -> status to Soft
        }
    Row(
        Modifier.clip(CircleShape)
            .background(color.copy(alpha = .1f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

fun timeLabel(value: String): String =
    runCatching {
            Instant.parse(value)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        .getOrDefault("待安排")

fun dateLabel(value: String): String =
    runCatching {
            Instant.parse(value)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("MM 月 dd 日"))
        }
        .getOrDefault("")

@Composable
fun CourtArt(modifier: Modifier = Modifier, bright: Boolean = false) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            Brush.radialGradient(
                listOf(Lime.copy(alpha = if (bright) .19f else .08f), Color.Transparent),
                Offset(w * .77f, h * .4f),
                w * .65f,
            ),
            w * .65f,
            Offset(w * .77f, h * .4f),
        )
        val court =
            Path().apply {
                moveTo(w * .24f, h * .86f)
                lineTo(w * .58f, h * .1f)
                lineTo(w * .98f, h * .28f)
                lineTo(w * .86f, h * .99f)
                close()
            }
        drawPath(court, Lime.copy(alpha = .04f))
        drawPath(court, Lime.copy(alpha = .32f), style = Stroke(1.5.dp.toPx()))
        fun line(ax: Float, ay: Float, bx: Float, by: Float, alpha: Float = .22f) {
            drawLine(
                Lime.copy(alpha = alpha),
                Offset(w * ax, h * ay),
                Offset(w * bx, h * by),
                1.dp.toPx(),
            )
        }
        line(.39f, .52f, .92f, .63f)
        line(.49f, .29f, .96f, .44f)
        line(.31f, .71f, .89f, .8f)
        line(.59f, .93f, .78f, .19f)
        line(.29f, .86f, .61f, .12f)
        line(.8f, .96f, .93f, .26f)
        // A shuttle silhouette, drawn natively so it stays sharp at every density.
        val cx = w * .76f
        val cy = h * .37f
        val scale = min(w, h) * .22f
        for (i in 0..5) {
            val angle = (-135 + i * 13) * PI.toFloat() / 180
            val tip = Offset(cx + cos(angle) * scale, cy + sin(angle) * scale)
            val feather =
                Path().apply {
                    moveTo(cx, cy)
                    lineTo(tip.x - scale * .11f, tip.y)
                    lineTo(tip.x, tip.y - scale * .15f)
                    lineTo(tip.x + scale * .1f, tip.y)
                    close()
                }
            drawPath(feather, Paper.copy(alpha = .75f - i * .045f))
        }
        drawCircle(Lime, scale * .15f, Offset(cx + scale * .06f, cy + scale * .05f))
        drawCircle(Ink, scale * .07f, Offset(cx + scale * .08f, cy + scale * .07f))
        repeat(35) { i ->
            val x = ((i * 71) % 997) / 997f * w
            val y = ((i * 43) % 251) / 251f * h
            drawCircle(Paper.copy(alpha = .11f), .7.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
fun MatchCard(match: Match, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PanelColor)
            .border(1.dp, Edge, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(match.round, color = Soft, fontSize = 11.sp, modifier = Modifier.weight(1f))
            StatusPill(match.status)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    match.sideA,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(match.unitA, color = Soft, fontSize = 10.sp, maxLines = 1)
            }
            val p = match.progress
            Text(
                if (p != null) "${Scoring.points(p.rallies,0)} : ${Scoring.points(p.rallies,1)}"
                else "VS",
                color = if (p == null) Soft else Lime,
                fontSize = if (p == null) 13.sp else 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    match.sideB,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(match.unitB, color = Soft, fontSize = 10.sp, maxLines = 1)
            }
        }
        HorizontalDivider(color = Edge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = Soft)
            Spacer(Modifier.width(5.dp))
            Text(timeLabel(match.startsAt), color = Soft, fontSize = 12.sp)
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Outlined.Place, null, Modifier.size(14.dp), tint = Soft)
            Spacer(Modifier.width(4.dp))
            Text(match.court, color = Soft, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ArrowForward, null, Modifier.size(17.dp), tint = Lime)
        }
    }
}

@Composable
fun MetaRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = Soft)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Soft, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EmptyState(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.SportsTennis, null, Modifier.size(38.dp), tint = Soft)
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = Soft, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RallyGrid(rallies: List<Int>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rallies.forEachIndexed { i, side ->
            Box(
                Modifier.size(23.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background((if (side == 0) Blue else Rose).copy(alpha = .2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${i+1}", color = if (side == 0) Blue else Rose, fontSize = 8.sp)
            }
        }
    }
}
