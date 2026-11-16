package cn.courtside.client

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import cn.courtside.shared.Models.*
import cn.courtside.shared.Scoring

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RefereeMatchDetail(state: UiState, vm: CourtsideViewModel) {
    val match = state.selected ?: return
    var toss by rememberSaveable(match.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("MATCH CENTER", Soft)
                        Text(
                            match.tournamentName,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusPill(match.status)
                }
            }
            item { RefereeDuelHero(match) }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    MetaRow(Icons.Outlined.CalendarMonth, "比赛日期", dateLabel(match.startsAt))
                    MetaRow(
                        Icons.Outlined.Schedule,
                        "预计时段",
                        "${timeLabel(match.startsAt)} — ${timeLabel(match.endsAt)}",
                    )
                    MetaRow(Icons.Outlined.Place, "执裁场地", match.court)
                    MetaRow(Icons.Outlined.Badge, "执裁裁判", match.refereeName)
                    HorizontalDivider(color = Edge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                                "${match.rules.gamesToWin*2-1} 局 ${match.rules.gamesToWin} 胜",
                                "${match.rules.target} 分制",
                                "领先 ${match.rules.lead} 分",
                                "${match.rules.cap} 分封顶",
                            )
                            .forEach { rule ->
                                Text(
                                    rule,
                                    fontSize = 10.sp,
                                    lineHeight = 16.sp,
                                    color = Lime,
                                    modifier =
                                        Modifier.background(
                                                Lime.copy(alpha = .07f),
                                                RoundedCornerShape(6.dp),
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                    }
                    Text(
                        "赛程版本 ${match.scheduleVersion}",
                        color = Soft,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            if (match.returnReason.isNotBlank())
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Eyebrow("CORRECTION / ${match.correctionRound}", Rose)
                        Text("赛事组退回更正", color = Rose, fontWeight = FontWeight.Bold)
                        Text(match.returnReason, fontSize = 13.sp, lineHeight = 20.sp)
                        Text("请核对原比分，修改后重新让双方确认并提交。", color = Soft, fontSize = 11.sp)
                    }
                }
            match.progress?.let { score ->
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Eyebrow("SESSION SCORE", Lime)
                                Text(
                                    if (match.status == "IN_PROGRESS") "最近同步的比分" else "比赛结果",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                "${Scoring.wins(score,0)} : ${Scoring.wins(score,1)}",
                                color = Lime,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                fontSize = 42.sp,
                                lineHeight = 48.sp,
                            )
                        }
                        HorizontalDivider(color = Edge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            score.games.forEachIndexed { index, game ->
                                Text(
                                    "G${index+1}  ${game.a} – ${game.b}",
                                    fontSize = 12.sp,
                                    color = Paper,
                                    modifier =
                                        Modifier.background(Ink, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                        if (match.status == "IN_PROGRESS")
                            Text(
                                "第 ${score.games.size+1} 局  ·  ${Scoring.points(score.rallies,0)} : ${Scoring.points(score.rallies,1)}",
                                color = Blue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        Text("最后更新 ${timeLabel(match.updatedAt)}", color = Soft, fontSize = 10.sp)
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(Ink).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            when (match.status) {
                "PUBLISHED" ->
                    PrimaryButton(
                        "接受执裁任务",
                        { vm.matchAction(match, "accept") },
                        Modifier.fillMaxWidth(),
                        !state.busy,
                        Icons.Outlined.TaskAlt,
                    )
                "ACCEPTED" ->
                    PrimaryButton(
                        "赛前准备与抽签",
                        { toss = true },
                        Modifier.fillMaxWidth(),
                        !state.busy,
                        Icons.Outlined.SportsTennis,
                    )
                "IN_PROGRESS" ->
                    PrimaryButton(
                        if (match.correctionRound > 0) "进入成绩更正" else "继续现场计分",
                        { vm.resume() },
                        Modifier.fillMaxWidth(),
                        !state.busy,
                        Icons.Outlined.PlayArrow,
                    )
                "PENDING_REVIEW",
                "FINISHED" ->
                    PrimaryButton(
                        "查看逐分回顾",
                        { vm.navigate("result") },
                        Modifier.fillMaxWidth(),
                        !state.busy,
                        Icons.Outlined.EmojiEvents,
                    )
            }
            Text("比分自动保存在本机 · 已开始后可离线计分", fontSize = 10.sp, lineHeight = 14.sp, color = Soft)
        }
    }
    if (toss)
        TossDialog(match, { toss = false }) { server ->
            toss = false
            vm.start(match, server)
        }
}

@Composable
private fun RefereeDuelHero(match: Match) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PanelColor)
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(Blue.copy(alpha = .4f), Edge, Rose.copy(alpha = .4f))
                ),
                RoundedCornerShape(24.dp),
            )
    ) {
        Column {
            EyebrowRow(match.round)
            Row(Modifier.fillMaxWidth().heightIn(min = 146.dp)) {
                DuelSide(match.sideA, match.unitA, 0, Modifier.weight(1f))
                DuelSide(match.sideB, match.unitB, 1, Modifier.weight(1f))
            }
        }
        Canvas(Modifier.matchParentSize()) {
            drawLine(
                Blue.copy(alpha = .55f),
                Offset(size.width * .1f, size.height - 1.dp.toPx()),
                Offset(size.width * .38f, size.height - 1.dp.toPx()),
                2.dp.toPx(),
            )
            drawLine(
                Rose.copy(alpha = .55f),
                Offset(size.width * .62f, size.height - 1.dp.toPx()),
                Offset(size.width * .9f, size.height - 1.dp.toPx()),
                2.dp.toPx(),
            )
        }
        Text(
            "VS",
            color = Lime,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            fontSize = 25.sp,
            letterSpacing = (-1).sp,
            modifier =
                Modifier.align(Alignment.Center)
                    .padding(top = 38.dp)
                    .background(Ink.copy(alpha = .8f), CutCornerShape(10.dp))
                    .padding(10.dp),
        )
    }
}

@Composable
private fun EyebrowRow(round: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            round,
            color = Lime,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("COURTSIDE / VS", color = Soft, fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun DuelSide(name: String, unit: String, side: Int, modifier: Modifier) {
    val accent = if (side == 0) Blue else Rose
    Box(modifier.heightIn(min = 146.dp).background(accent.copy(alpha = .05f))) {
        Image(
            painterResource(
                if (side == 0) R.drawable.referee_detail_lin_dan else R.drawable.referee_detail_lee
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            alpha = .3f,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(PanelColor.copy(alpha = .3f), PanelColor.copy(alpha = .88f))
                    )
                )
        )
        Column(
            Modifier.fillMaxWidth()
                .padding(
                    start = if (side == 0) 18.dp else 25.dp,
                    end = if (side == 0) 25.dp else 18.dp,
                    top = 18.dp,
                    bottom = 18.dp,
                ),
            horizontalAlignment = if (side == 0) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Eyebrow(if (side == 0) "蓝方 / A" else "红方 / B", accent)
            Text(
                name,
                color = accent,
                fontFamily = PlayerScript,
                fontSize = if (name.length > 12) 23.sp else if (name.length > 7) 27.sp else 34.sp,
                lineHeight = 40.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign =
                    if (side == 0) androidx.compose.ui.text.style.TextAlign.Start
                    else androidx.compose.ui.text.style.TextAlign.End,
            )
            Text(
                unit,
                color = Soft,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
