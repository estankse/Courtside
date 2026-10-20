@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cn.courtside.client

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import cn.courtside.shared.Models.*
import cn.courtside.shared.Scoring
import kotlinx.coroutines.delay

@Composable
fun ScoreScreen(state: UiState, vm: CourtsideViewModel) {
    val m = state.selected ?: return
    val s = state.score ?: return
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    var bright by rememberSaveable { mutableStateOf(false) }
    var retire by remember { mutableStateOf(false) }
    val a = Scoring.points(s.rallies, 0)
    val b = Scoring.points(s.rallies, 1)
    val over = Scoring.gameOver(a, b, m.rules)
    val complete = Scoring.winner(s, m.rules) >= 0
    val active =
        m.status == "IN_PROGRESS" &&
            !s.submitted &&
            !over &&
            !complete &&
            s.breakUntil <= System.currentTimeMillis()
    val fg = if (bright) Ink else Paper
    val muted = if (bright) Color(0xFF526477) else Soft
    Column(
        Modifier.fillMaxSize()
            .background(if (bright) Color(0xFFEAF0F4) else Ink)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.navigate("home") }) {
                Icon(Icons.Outlined.ArrowBack, "保存并返回工作台", tint = fg)
            }
            Column(Modifier.weight(1f)) {
                Text(m.court, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg)
                Text("${m.round} · 第 ${s.games.size+1} 局", fontSize = 10.sp, color = muted)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    if (state.online) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                    null,
                    tint =
                        if (state.online) {
                            if (bright) Color(0xFF366637) else Lime
                        } else {
                            if (bright) Color(0xFFAA3156) else Rose
                        },
                    modifier = Modifier.size(15.dp),
                )
                Text(if (state.online) "本机已保存" else "离线计分", color = muted, fontSize = 10.sp)
            }
            IconButton(onClick = { bright = !bright }) {
                Icon(
                    if (bright) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    "切换明暗计分模式",
                    tint = fg,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${m.rules.target} 分制", color = muted, fontSize = 11.sp)
            Text("  /  ", color = muted)
            Text(
                "${m.rules.gamesToWin*2-1} 局 ${m.rules.gamesToWin} 胜",
                color = muted,
                fontSize = 11.sp,
            )
            if (a >= m.rules.target - 1 && b >= m.rules.target - 1)
                Text(
                    "   加时赛",
                    color = if (bright) Color(0xFF1A5A90) else Lime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxHeight < 300.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val order = if (s.swapped) listOf(1, 0) else listOf(0, 1)
                order.forEach { side ->
                    PlayerScore(
                        m,
                        s,
                        side,
                        active,
                        bright,
                        compact,
                        Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        vm.add(side)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ScoreTool(Icons.Outlined.Undo, "撤销", s.rallies.isNotEmpty() && !s.submitted, muted) {
                vm.undo()
            }
            ScoreTool(Icons.Outlined.SwapHoriz, "交换显示", !s.submitted, muted) { vm.swap() }
            ScoreTool(Icons.Outlined.Flag, "弃权处理", !s.submitted && !complete, muted) {
                retire = true
            }
            ScoreTool(Icons.Outlined.Save, "保存返回", true, muted) { vm.navigate("home") }
        }
        Text(
            "点击对应一侧记 1 分 · 长亮屏幕 · 操作自动保存",
            color = muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
    }
    if (s.breakUntil > 0 && !complete) BreakDialog(s, m) { vm.endBreak() }
    if (over && !s.submitted)
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.EmojiEvents, null, tint = Lime) },
            title = { Text("本局结束，请核对") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${m.sideA}  $a : $b  ${m.sideB}",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("确认后将封存本局记录。误触最后一分时，可先撤销。", fontSize = 12.sp, color = Soft)
                }
            },
            confirmButton = { TextButton(onClick = { vm.finishGame() }) { Text("确认本局结果") } },
            dismissButton = { TextButton(onClick = { vm.undo() }) { Text("撤销最后一分", color = Rose) } },
        )
    if (complete && !s.submitted) LaunchedEffect(complete) { vm.navigate("result") }
    if (retire)
        RetirementDialog(m, { retire = false }) { side, reason ->
            retire = false
            vm.retire(side, reason)
        }
}

@Composable
private fun PlayerScore(
    m: Match,
    s: ScoreSession,
    side: Int,
    enabled: Boolean,
    bright: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onScore: () -> Unit,
) {
    val accent = if (side == 0) Blue else Rose
    val darkAccent = if (side == 0) Color(0xFF18518E) else Color(0xFFAA3156)
    val name = if (side == 0) m.sideA else m.sideB
    val unit = if (side == 0) m.unitA else m.unitB
    val score = Scoring.points(s.rallies, side)
    val wins = Scoring.wins(s, side)
    val serving = Scoring.server(s) == side
    val point = Scoring.gamePoint(s, m.rules, side)
    val pointLabel =
        if (point) if (wins == m.rules.gamesToWin - 1) "MATCH POINT · 赛点" else "GAME POINT · 局点"
        else ""
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .98f else 1f, label = "score press")
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(
                CutCornerShape(
                    topEnd = if (side == 0) 26.dp else 10.dp,
                    bottomStart = if (side == 0) 10.dp else 26.dp,
                )
            )
            .background(
                if (bright) Brush.verticalGradient(listOf(Color.White, accent.copy(alpha = .25f)))
                else Brush.verticalGradient(listOf(accent.copy(alpha = .17f), PanelColor))
            )
            .border(
                1.dp,
                accent.copy(alpha = .35f),
                CutCornerShape(
                    topEnd = if (side == 0) 26.dp else 10.dp,
                    bottomStart = if (side == 0) 10.dp else 26.dp,
                ),
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onScore()
            }
            .semantics { contentDescription = "$name 加一分，当前 $score 分" }
    ) {
        val numberSize =
            minOf(maxWidth.value * .66f, if (compact) maxHeight.value * .35f else 128f)
                .coerceAtLeast(40f)
        Column(
            Modifier.fillMaxSize().padding(if (compact) 12.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(
                    if (side == 0) "SIDE / A" else "SIDE / B",
                    if (bright) darkAccent else accent,
                )
                Spacer(Modifier.weight(1f))
                repeat(m.rules.gamesToWin) { i ->
                    Box(
                        Modifier.padding(start = 4.dp)
                            .size(7.dp)
                            .background(
                                if (i < wins) if (bright) darkAccent else accent
                                else accent.copy(alpha = .16f),
                                CircleShape,
                            )
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 6.dp else 18.dp))
            Text(
                name,
                color = if (bright) Ink else Paper,
                fontSize = if (compact) 24.sp else 31.sp,
                fontFamily = PlayerScript,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (!compact)
                Text(
                    unit,
                    color = if (bright) Color(0xFF526477) else Soft,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    score,
                    label = "score",
                    transitionSpec = {
                        fadeIn(tween(130)) + slideInVertically(tween(130)) { it / 5 } togetherWith
                            fadeOut(tween(90))
                    },
                ) { value ->
                    Text(
                        value.toString().padStart(2, '0'),
                        color = if (bright) darkAccent else accent,
                        fontSize = numberSize.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-3).sp,
                        lineHeight = numberSize.sp,
                    )
                }
            }
            if (pointLabel.isNotEmpty())
                Text(
                    pointLabel,
                    color = if (bright) darkAccent else accent,
                    fontSize = if (compact) 8.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp),
                    maxLines = 1,
                )
            Row(
                Modifier.clip(CircleShape)
                    .background(accent.copy(alpha = if (serving) .16f else .04f))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (serving) Icons.Outlined.SportsTennis else Icons.Outlined.Remove,
                    null,
                    Modifier.size(12.dp),
                    tint = if (bright) darkAccent else accent,
                )
                Text(
                    if (serving) "发球 · ${if(score%2==0)"右区" else "左区"}" else "接发球",
                    color = if (bright) darkAccent else accent,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(if (compact) 5.dp else 16.dp))
            Text(
                "＋ 1",
                color = if (bright) darkAccent else accent,
                fontSize = if (compact) 17.sp else 23.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ScoreTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    color: Color = Soft,
    onClick: () -> Unit,
) {
    Column(
        Modifier.widthIn(min = 62.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            label,
            Modifier.size(23.dp),
            tint = if (enabled) color else color.copy(alpha = .3f),
        )
        Text(label, color = if (enabled) color else color.copy(alpha = .3f), fontSize = 10.sp)
    }
}

@Composable
private fun BreakDialog(s: ScoreSession, m: Match, onFinish: () -> Unit) {
    var remaining by
        remember(s.breakUntil) {
            mutableLongStateOf(
                ((s.breakUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            )
        }
    val interval = s.rallies.isNotEmpty()
    val deciding =
        Scoring.wins(s, 0) == m.rules.gamesToWin - 1 && Scoring.wins(s, 1) == m.rules.gamesToWin - 1
    LaunchedEffect(s.breakUntil) {
        while (true) {
            remaining = ((s.breakUntil - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0)
            if (remaining == 0L) {
                onFinish()
                break
            }
            delay(250)
        }
    }
    Dialog(onDismissRequest = {}) {
        Panel {
            Eyebrow("TAKE A BREATH", Lime)
            Text(if (interval) "中场间歇" else "局间休息", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(
                "${remaining/60}:${(remaining%60).toString().padStart(2,'0')}",
                fontSize = 76.sp,
                fontWeight = FontWeight.Black,
                color = Lime,
            )
            Text(if (!interval || deciding) "请提醒双方交换场地" else "请提醒选手补水，准备继续比赛", fontSize = 14.sp)
            Text(
                "接下来由 ${if(Scoring.server(s)==0)m.sideA else m.sideB} 发球",
                fontSize = 12.sp,
                color = Soft,
            )
            PrimaryButton("结束间歇，继续比赛", onFinish, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RetirementDialog(m: Match, onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var winner by remember { mutableStateOf("0") }
    var reason by remember { mutableStateOf("") }
    FormDialog(
        "弃权或中止比赛",
        onDismiss,
        "确认结果",
        reason.isNotBlank(),
        { onConfirm(winner.toInt(), reason) },
    ) {
        Text("选择获胜方并填写原因。系统会保留已进行的计分过程，结果将交由赛事组核验。", fontSize = 12.sp, color = Soft)
        SelectField("获胜方", listOf("0" to m.sideA, "1" to m.sideB), winner) { winner = it }
        Input(reason, "中止或弃权原因", { reason = it })
    }
}

@Composable
fun ResultScreen(state: UiState, vm: CourtsideViewModel) {
    val m = state.selected ?: return
    val receipt =
        state.feed.submissions.find { it.id == m.currentSubmissionId }
            ?: state.workspace?.submissions?.find { it.id == m.currentSubmissionId }
    val s = state.score ?: receipt?.score ?: m.progress
    if (s == null) {
        EmptyState("暂无计分记录", "请先完成一场比赛。")
        return
    }
    val winner = Scoring.winner(s, m.rules)
    val readOnly = s.submitted || m.status in listOf("PENDING_REVIEW", "FINISHED")
    var edit by remember { mutableIntStateOf(-1) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF25372B), PanelColor)))
            ) {
                CourtArt(Modifier.matchParentSize())
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(40.dp), tint = Lime)
                    Eyebrow(if (m.status == "FINISHED") "OFFICIAL RESULT" else "MATCH RECAP", Lime)
                    Text(
                        if (winner >= 0) "${if(winner==0)m.sideA else m.sideB} 获胜" else "比分待更正",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "${Scoring.wins(s,0)}  :  ${Scoring.wins(s,1)}",
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Black,
                        color = Lime,
                    )
                    Text("${m.sideA}  /  ${m.sideB}", color = Soft, fontSize = 13.sp)
                    Text(
                        when {
                            m.status == "FINISHED" -> "赛事组已确认 · 正式成绩"
                            m.status == "PENDING_REVIEW" -> "云端已接收 · 等待赛事组核验"
                            s.submitted -> "本机已保存 · 等待云端接收"
                            m.correctionRound > 0 -> "更正批次 ${m.correctionRound} · 重新确认后提交"
                            else -> "请双方核对后确认"
                        },
                        color = if (s.submitted && m.status == "IN_PROGRESS") Rose else Soft,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        state.issue?.let { issue ->
            item {
                Panel {
                    Text("操作提示", color = Rose, fontWeight = FontWeight.Bold)
                    Text(issue, fontSize = 12.sp)
                }
            }
        }
        if (m.returnReason.isNotBlank() && !readOnly)
            item {
                Panel {
                    Text("赛事组更正要求", color = Rose, fontWeight = FontWeight.Bold)
                    Text(m.returnReason, fontSize = 13.sp)
                    Text("更正局分将单独标注，原始逐分记录与旧提交仍保留。", color = Soft, fontSize = 11.sp)
                }
            }
        if (s.retirementWinner >= 0)
            item {
                Panel {
                    Text("弃权 / 中止记录", color = Rose, fontWeight = FontWeight.Bold)
                    Text(s.retirementReason, fontSize = 13.sp)
                }
            }
        item { SectionTitle("每一分的来路") }
        s.games.forEachIndexed { i, g ->
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "第 ${i+1} 局",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${g.a} – ${g.b}", fontSize = 23.sp, fontWeight = FontWeight.Black)
                        if (s.correctionRound > 0 && !readOnly)
                            ActionIcon(Icons.Outlined.Edit, "更正第 ${i+1} 局", { edit = i })
                    }
                    RallyGrid(g.rallies)
                    if (g.corrected)
                        Text("更正比分 · ${g.correctionNote}", color = Rose, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("■ ${m.sideA}", color = Blue, fontSize = 10.sp)
                        Text("■ ${m.sideB}", color = Rose, fontSize = 10.sp)
                    }
                }
            }
        }
        if (s.rallies.isNotEmpty())
            item {
                Panel {
                    Text("中止前本局记录", fontWeight = FontWeight.Bold)
                    Text(
                        "${Scoring.points(s.rallies,0)} – ${Scoring.points(s.rallies,1)}",
                        color = Soft,
                    )
                    RallyGrid(s.rallies)
                }
            }
        if (!readOnly) {
            item {
                SectionTitle("选手现场确认")
                Text("请双方查看局分及过程，分别点击自己的确认按钮。", color = Soft, fontSize = 12.sp)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Signature(m.sideA, s.aConfirmed, Blue, Modifier.weight(1f)) { vm.sign(0) }
                    Signature(m.sideB, s.bConfirmed, Rose, Modifier.weight(1f)) { vm.sign(1) }
                }
            }
            item {
                PrimaryButton(
                    "确认结果并提交云端",
                    { vm.submit() },
                    Modifier.fillMaxWidth(),
                    winner >= 0 && (s.retirementWinner >= 0 || (s.aConfirmed && s.bConfirmed)),
                    Icons.Outlined.CloudUpload,
                )
            }
            if (winner < 0)
                item {
                    OutlinedButton(
                        onClick = { vm.navigate("score") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("继续记录后续比赛")
                    }
                }
        } else {
            item {
                Panel {
                    Text(
                        "${if(s.aConfirmed)"蓝方已确认" else "蓝方未确认"}  ·  ${if(s.bConfirmed)"红方已确认" else "红方未确认"}",
                        fontSize = 12.sp,
                        color = Soft,
                    )
                    Text(
                        "提交编号 ${(s.submissionId.ifBlank{receipt?.id.orEmpty()}).take(12)}",
                        fontSize = 10.sp,
                        color = Soft,
                    )
                }
            }
            if (m.status == "IN_PROGRESS" && s.submitted)
                item {
                    PrimaryButton(
                        "重试云端同步",
                        { vm.refreshNow() },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Sync,
                    )
                }
            item { PrimaryButton("返回工作台", { vm.navigate("home") }, Modifier.fillMaxWidth()) }
        }
    }
    if (edit >= 0) {
        val g = s.games[edit]
        var a by remember(edit) { mutableStateOf(g.a.toString()) }
        var b by remember(edit) { mutableStateOf(g.b.toString()) }
        var note by remember(edit) { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        FormDialog(
            "更正第 ${edit+1} 局",
            { edit = -1 },
            onConfirm = {
                val av = a.toIntOrNull()
                val bv = b.toIntOrNull()
                if (
                    av != null &&
                        bv != null &&
                        av >= 0 &&
                        bv >= 0 &&
                        av <= m.rules.cap &&
                        bv <= m.rules.cap &&
                        Scoring.gameOver(av, bv, m.rules) &&
                        note.isNotBlank()
                ) {
                    vm.correctGame(edit, av, bv, note)
                    edit = -1
                } else error = "请输入有效的完赛比分并填写原因"
            },
        ) {
            Input(a, "${m.sideA} 得分", { a = it }, true)
            Input(b, "${m.sideB} 得分", { b = it }, true)
            Input(note, "更正原因", { note = it })
            if (error.isNotBlank()) Text(error, color = Rose, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Signature(
    name: String,
    confirmed: Boolean,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = if (confirmed) .18f else .07f))
            .border(
                1.dp,
                color.copy(alpha = if (confirmed) .8f else .2f),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (confirmed) Icons.Outlined.TaskAlt else Icons.Outlined.Draw,
            null,
            tint = color,
            modifier = Modifier.size(27.dp),
        )
        Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(if (confirmed) "已确认 · 点击可撤回" else "点击确认成绩", fontSize = 10.sp, color = color)
    }
}
