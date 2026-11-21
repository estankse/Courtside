@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cn.courtside.client

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import cn.courtside.shared.Models.*
import cn.courtside.shared.Scoring
import java.time.Instant
import kotlinx.coroutines.delay

/** Organizer presentation only: the referee's card and scoring controls stay unchanged. */
internal fun organizerScoreLabel(match: Match): String {
    val score = match.progress ?: return "VS"
    if (score.retirementWinner >= 0) return "弃权"
    if (match.status in listOf("PENDING_REVIEW", "FINISHED") || Scoring.winner(score, match.rules) >= 0)
        return "局胜 ${Scoring.wins(score, 0)} : ${Scoring.wins(score, 1)}"
    return "第${score.games.size + 1}局 ${Scoring.points(score.rallies, 0)} : ${Scoring.points(score.rallies, 1)}"
}

internal fun entrantLabel(entrant: Entrant): String =
    listOf(entrant.name, entrant.unit.takeIf { it.isNotBlank() }, "编号 ${entrant.id.take(8)}")
        .filterNotNull().joinToString(" · ")

internal fun refereeAccountError(username: String, password: String): String? = when {
    !username.matches(Regex("[a-zA-Z0-9_.-]{3,40}")) -> "账号需为3～40位字母、数字、下划线、点或短横线"
    password.length !in 10..72 -> "密码长度需为10～72位"
    password != password.trim() -> "密码首尾不能包含空白字符"
    else -> null
}

internal fun organizerRuleErrors(target: String, cap: String, wins: String, lead: String): Map<String, String> =
    buildMap {
        val targetValue = target.toIntOrNull()
        val capValue = cap.toIntOrNull()
        if (targetValue == null || targetValue !in 3..30) put("target", "每局目标分需为3～30的整数")
        if (wins.toIntOrNull() !in 1..3) put("wins", "获胜所需局数需为1～3的整数")
        if (lead.toIntOrNull() !in 1..3) put("lead", "最小领先分需为1～3的整数")
        if (capValue == null || capValue !in 3..40) put("cap", "封顶分需为3～40的整数")
        else if (targetValue != null && capValue < targetValue) put("cap", "封顶分不能小于目标分")
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrganizerSelectField(
    label: String,
    choices: List<Pair<String, String>>,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    ExposedDropdownMenuBox(expanded, {
        if (enabled) {
            focus.clearFocus()
            keyboard?.hide()
            expanded = !expanded
        }
    }) {
        OutlinedTextField(
            choices.find { it.first == value }?.second.orEmpty(), {}, readOnly = true,
            enabled = enabled, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        )
        ExposedDropdownMenu(expanded && enabled, { expanded = false }) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onChange(id); expanded = false })
            }
        }
    }
}

internal fun archiveBlockReason(work: Workspace): String? {
    if (work.tournament.status == "ARCHIVED") return "赛事已归档，资料仅供查询"
    if (work.matches.isEmpty()) return "空赛事不能归档，请先编排并完成比赛"
    val remaining = work.matches.count { it.status !in listOf("FINISHED", "CANCELLED") }
    return if (remaining > 0) "尚有 $remaining 场比赛未完成或取消，暂不能归档" else null
}

@Composable
internal fun OrganizerFormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "保存",
    enabled: Boolean = true,
    busy: Boolean = false,
    error: String? = null,
    readOnly: Boolean = false,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Read the host window's insets before entering the separate Dialog window.
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
        ),
    ) {
        val dialogView = LocalView.current
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            val previousMode = window?.attributes?.softInputMode
            // Let imePadding resize the content instead of moving the entire window.
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            }
            onDispose { if (previousMode != null) window.setSoftInputMode(previousMode) }
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(safeInsets).imePadding().padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxHeight < 360.dp
            val confirmText = if (busy) "正在提交…" else if (readOnly) "关闭" else confirmLabel
            val confirmAction: () -> Unit = {
                focus.clearFocus()
                keyboard?.hide()
                if (readOnly) onDismiss() else onConfirm()
            }
            val canConfirm = !busy && (readOnly || enabled)
            Surface(
                Modifier.padding(if (compact) 0.dp else 8.dp).widthIn(max = 640.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp), color = PanelColor,
            ) {
                Column(Modifier.padding(if (compact) 8.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), fontSize = if (compact) 18.sp else 22.sp, fontWeight = FontWeight.Bold,
                            maxLines = if (compact) 1 else Int.MAX_VALUE,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (compact) TextButton(onClick = confirmAction, enabled = canConfirm) { Text(confirmText) }
                        ActionIcon(Icons.Outlined.Close, "关闭", onDismiss, !busy)
                    }
                    Column(
                        Modifier.weight(1f, false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        content()
                        if (!error.isNullOrBlank()) Text(error, color = Rose, fontSize = 14.sp)
                    }
                    if (!compact) PrimaryButton(confirmText, confirmAction, Modifier.fillMaxWidth(), canConfirm)
                }
            }
        }
    }
}

@Composable
internal fun OrganizerInput(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    numeric: Boolean = false,
    error: String? = null,
) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        enabled = enabled, singleLine = true, isError = error != null,
        supportingText = error?.let { { Text(it) } },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
    )
}

@Composable
internal fun OrganizerPlayers(match: Match) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("蓝方 · ${match.sideA}", color = Blue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (match.unitA.isNotBlank()) Text(match.unitA, color = Soft, fontSize = 14.sp)
        Text("红方 · ${match.sideB}", color = Rose, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (match.unitB.isNotBlank()) Text(match.unitB, color = Soft, fontSize = 14.sp)
    }
}

@Composable
internal fun OrganizerMatchMetadata(match: Match) {
    Panel(Modifier.fillMaxWidth()) {
        Text("比赛日期", color = Soft, fontSize = 14.sp)
        Text(dateLabel(match.startsAt))
        Text("预计时段", color = Soft, fontSize = 14.sp)
        Text(organizerMatchTimeRange(match))
        Text("执裁场地", color = Soft, fontSize = 14.sp)
        Text(match.court)
        Text("执裁裁判", color = Soft, fontSize = 14.sp)
        Text(match.refereeName)
        HorizontalDivider(color = Edge)
        Text("${match.rules.gamesToWin * 2 - 1} 局 ${match.rules.gamesToWin} 胜 · ${match.rules.target} 分制 · 领先 ${match.rules.lead} 分 · ${match.rules.cap} 分封顶",
            color = Lime, fontSize = 14.sp)
        Text("安排版本 ${match.scheduleVersion} · 场次 ${match.id.take(8).uppercase()}", color = Soft, fontSize = 12.sp)
    }
}

@Composable
internal fun OrganizerMatchCard(match: Match, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(match.round, color = Soft, fontSize = 14.sp)
            StatusPill(match.status)
        }
        OrganizerPlayers(match)
        Text(organizerScoreLabel(match), color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        match.progress?.let { score ->
            val winner = Scoring.winner(score, match.rules)
            if (winner >= 0) Text("${if (winner == 0) match.sideA else match.sideB} 获胜", fontSize = 14.sp)
            else Text("局胜 ${Scoring.wins(score, 0)} : ${Scoring.wins(score, 1)}", color = Soft, fontSize = 14.sp)
        }
        Text("${organizerMatchTimeRange(match)} · ${match.court}", color = Soft, fontSize = 14.sp)
        Text("裁判 ${match.refereeName}", color = Soft, fontSize = 14.sp)
    }
}

@Composable
internal fun OrganizerScoreDetails(match: Match, refreshedAt: String = "", refreshFailed: Boolean = false) {
    val score = match.progress
    Panel(Modifier.fillMaxWidth()) {
        Text(if (match.status == "IN_PROGRESS") "最近同步的比分" else "比赛结果", fontWeight = FontWeight.Bold)
        if (score != null) OrganizerScoreSummary(match, score)
        val receivedAt = remember(match.scoreReceivedAt) {
            runCatching { Instant.parse(match.scoreReceivedAt) }.getOrNull()
        }
        when {
            score == null -> Text("尚未收到比分快照", color = Soft)
            receivedAt == null -> Text("历史比分未记录上传时间", color = Soft)
            else -> Text("比分最后上传 ${organizerRecordTime(match.scoreReceivedAt)}", color = Soft)
        }
        if (match.status == "IN_PROGRESS" && receivedAt != null) {
            var stale by remember(receivedAt) { mutableStateOf(!receivedAt.plusSeconds(30).isAfter(Instant.now())) }
            LaunchedEffect(receivedAt) {
                val remaining = receivedAt.plusSeconds(30).toEpochMilli() - System.currentTimeMillis()
                if (remaining > 0) delay(remaining)
                stale = true
            }
            if (stale) Text("超过 30 秒未收到新比分", color = Rose)
        }
        if (refreshedAt.isNotBlank()) Text("页面上次刷新 ${organizerRecordTime(refreshedAt)}", color = Soft)
        if (refreshFailed) Text("刷新失败，当前显示上次读取的数据", color = Rose)
    }
}

@Composable
private fun OrganizerScoreSummary(match: Match, score: ScoreSession) {
    Text("局胜 ${Scoring.wins(score, 0)} : ${Scoring.wins(score, 1)}", fontSize = 26.sp, color = Lime)
    score.games.forEachIndexed { i, game ->
        Text("第 ${i + 1} 局  ${game.a} – ${game.b}", fontSize = 16.sp)
    }
    if (score.retirementWinner >= 0) {
        Text("弃权结束 · ${score.retirementReason}", color = Rose, fontSize = 14.sp)
    }
    val winner = Scoring.winner(score, match.rules)
    if (score.rallies.isNotEmpty() || (match.status == "IN_PROGRESS" && winner < 0)) {
        Text("第 ${score.games.size + 1} 局（未封存） ${Scoring.points(score.rallies, 0)} – ${Scoring.points(score.rallies, 1)}",
            color = Blue, fontSize = 16.sp)
    }
    if (winner >= 0) Text("${if (winner == 0) match.sideA else match.sideB} 获胜", color = Lime)
}

@Composable
internal fun OrganizerReasonDialog(
    title: String,
    detail: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    reason: String,
    onReasonChange: (String) -> Unit,
    enabled: Boolean = true,
    onConfirm: (String) -> Unit,
) {
    OrganizerFormDialog(title, onDismiss, enabled = enabled && reason.isNotBlank(), busy = busy, error = error,
        onConfirm = { onConfirm(reason) }) {
        Text(detail, color = Soft, fontSize = 14.sp)
        OrganizerInput(reason, "原因", onReasonChange, enabled = !busy)
    }
}
