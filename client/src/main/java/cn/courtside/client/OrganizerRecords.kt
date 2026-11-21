package cn.courtside.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.courtside.shared.Models.Audit
import cn.courtside.shared.Models.Match
import cn.courtside.shared.Models.Submission
import cn.courtside.shared.Models.Workspace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val organizerMatchStatuses = listOf(
    "DRAFT" to "草稿",
    "PUBLISHED" to "待接受",
    "ACCEPTED" to "已接受",
    "IN_PROGRESS" to "进行中",
    "PENDING_REVIEW" to "待核验",
    "FINISHED" to "已完成",
    "CANCELLED" to "已取消",
)

private val organizerZone = ZoneId.of("Asia/Shanghai")
private val recordTimeFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
private fun recordInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

internal fun organizerMatchDate(match: Match): String =
    recordInstant(match.startsAt)?.atZone(organizerZone)?.toLocalDate()?.toString().orEmpty()

internal fun organizerRecordTime(value: String): String =
    recordInstant(value)?.atZone(organizerZone)?.format(recordTimeFormat) ?: "未记录"

internal fun organizerMatchTimeRange(match: Match): String {
    val start = recordInstant(match.startsAt)?.atZone(organizerZone)
    val end = recordInstant(match.endsAt)?.atZone(organizerZone)
    val endDate = if (start != null && end != null && start.toLocalDate() != end.toLocalDate()) {
        "${end.toLocalDate()} "
    } else ""
    return "${timeLabel(match.startsAt)} — $endDate${timeLabel(match.endsAt)}"
}

internal fun organizerSubmissionStatus(submission: Submission): String = when (submission.status) {
    "PENDING_REVIEW" -> "待核验"
    "CONFIRMED" -> "已确认"
    "RETURNED" -> "已退回"
    else -> submission.status
}

internal fun organizerSubmissionHistory(workspace: Workspace?, match: Match): List<Submission> =
    workspace?.submissions.orEmpty()
        .filter { it.matchId == match.id }
        .sortedWith(compareByDescending<Submission> { it.score.correctionRound }
            .thenByDescending { recordInstant(it.createdAt) })

internal fun isCurrentOrganizerSubmission(submission: Submission, match: Match): Boolean =
    submission.id == match.currentSubmissionId &&
        (match.status == "PENDING_REVIEW" && submission.status == "PENDING_REVIEW" ||
            match.status == "FINISHED" && submission.status == "CONFIRMED")

@Composable
internal fun OrganizerSubmissionRecordMetadata(submission: Submission, match: Match) {
    val current = if (isCurrentOrganizerSubmission(submission, match)) " · 当前有效提交" else ""
    Text("${organizerSubmissionStatus(submission)} · 第 ${submission.score.correctionRound + 1} 次提交$current",
        fontWeight = FontWeight.Bold)
    Text("提交时间 ${organizerRecordTime(submission.createdAt)}", color = Soft, fontSize = 14.sp)
    if (submission.status != "PENDING_REVIEW" || submission.reviewedAt.isNotBlank()) {
        Text("处理时间 ${organizerRecordTime(submission.reviewedAt)}", color = Soft, fontSize = 14.sp)
    }
    if (submission.reviewReason.isNotBlank()) {
        Text("退回原因", color = Soft, fontSize = 14.sp)
        Text(submission.reviewReason)
    }
}

internal fun LazyListScope.organizerSchedule(
    workspace: Workspace,
    date: String,
    courtId: String,
    status: String,
    onOpen: (Match) -> Unit,
) {
    val shown = workspace.matches.filter {
        (date.isEmpty() || organizerMatchDate(it) == date) &&
            (courtId.isEmpty() || it.courtId == courtId) &&
            (status.isEmpty() || it.status == status)
    }
    if (shown.isEmpty()) {
        item { EmptyState("暂无符合条件的比赛", "可调整筛选条件，或清空筛选查看全部赛程。") }
    } else if (date.isNotEmpty() || courtId.isNotEmpty() || status.isNotEmpty()) {
        item { Text("符合条件 ${shown.size} 场", color = Soft) }
    }
    shown.groupBy(::organizerMatchDate).forEach { (day, matches) ->
        item { SectionTitle(day.ifBlank { "日期待安排" }) }
        items(matches, key = { it.id }) { match -> OrganizerMatchCard(match, { onOpen(match) }) }
    }
}

@Composable
internal fun OrganizerScheduleFilters(
    workspace: Workspace,
    date: String,
    courtId: String,
    status: String,
    onDate: (String) -> Unit,
    onCourt: (String) -> Unit,
    onStatus: (String) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val dates = workspace.matches.map(::organizerMatchDate).filter { it.isNotEmpty() }.distinct().sorted()
    val courts = buildMap {
        workspace.courts.forEach { put(it.id, it.name) }
        workspace.matches.filter { it.courtId.isNotBlank() }.forEach { putIfAbsent(it.courtId, it.court) }
    }
    val courtNames = courts.values.groupingBy { it }.eachCount()
    val courtOptions = courts.map { (id, name) ->
        id to if (courtNames[name] == 1 && name.isNotBlank()) name else "${name.ifBlank { "场地" }} · ${id.take(8)}"
    }
    val filterCount = listOf(date, courtId, status).count { it.isNotEmpty() }
    Panel(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "收起筛选" else if (filterCount == 0) "筛选赛程" else "筛选赛程 · $filterCount 项")
        }
        if (expanded) {
            OrganizerSelectField("比赛日期", listOf("" to "全部日期") + dates.map { it to it },
                date, true, onDate)
            OrganizerSelectField("比赛场地", listOf("" to "全部场地") + courtOptions, courtId, true, onCourt)
            OrganizerSelectField("比赛状态", listOf("" to "全部状态") + organizerMatchStatuses,
                status, true, onStatus)
        }
        if (filterCount > 0) {
            OutlinedButton(onClick = onClear) { Text("清空筛选") }
        }
    }
}

@Composable
internal fun OrganizerAuditScreen(workspace: Workspace) {
    var action by rememberSaveable(workspace.tournament.id) { mutableStateOf("") }
    var scope by rememberSaveable(workspace.tournament.id) { mutableStateOf("") }
    val matches = workspace.matches.associateBy { it.id }
    val records = workspace.audit.sortedByDescending { recordInstant(it.at) }
    val matchIds = records.map { it.matchId }.distinct()
    val scopes = listOf("" to "全部场次") + matchIds.map { id ->
        val match = matches[id]
        when {
            id.isBlank() -> "tournament" to "赛事级操作"
            match == null -> id to "场次 ${id.take(8)}"
            else -> id to "${match.round} · ${match.sideA} 对 ${match.sideB} · ${id.take(8)}"
        }
    }
    val actions = listOf("" to "全部操作") + records.map { it.action }.distinct().map { it to it }
    val shown = records.filter {
        (action.isEmpty() || it.action == action) &&
            (scope.isEmpty() || scope == it.matchId || scope == "tournament" && it.matchId.isBlank())
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (records.isNotEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    OrganizerSelectField("按场次筛选", scopes, scope, true) { scope = it }
                    OrganizerSelectField("按操作筛选", actions, action, true) { action = it }
                    if (scope.isNotEmpty() || action.isNotEmpty()) {
                        OutlinedButton(onClick = { scope = ""; action = "" }) { Text("清空筛选") }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                if (records.isEmpty()) EmptyState("暂无操作记录", "当前赛事尚无已记录的操作。")
                else EmptyState("暂无符合条件的操作", "可调整或清空筛选条件。")
            }
        }
        items(shown, key = { it.id }) { record -> OrganizerAuditRecord(record, matches[record.matchId]) }
    }
}

@Composable
private fun OrganizerAuditRecord(record: Audit, match: Match?) {
    Panel(Modifier.fillMaxWidth()) {
        Text(record.action, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("${organizerRecordTime(record.at)} · ${record.actor.ifBlank { "操作者未记录" }}", color = Soft)
        if (record.matchId.isNotBlank()) {
            if (match != null) Text("${match.round} · ${match.sideA} 对 ${match.sideB}")
            Text("场次编号 ${record.matchId}", color = Soft)
        }
        if (record.detail.isNotBlank()) Text(record.detail)
    }
}
