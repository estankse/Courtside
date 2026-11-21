@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cn.courtside.client

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import cn.courtside.shared.Models.*
import cn.courtside.shared.Scoring
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.random.Random

@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "保存",
    enabled: Boolean = true,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(25.dp),
            color = PanelColor,
            modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    ActionIcon(Icons.Outlined.Close, "关闭", onDismiss)
                }
                Column(
                    Modifier.weight(1f, false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                PrimaryButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), enabled)
            }
        }
    }
}

@Composable
fun Input(value: String, label: String, onChange: (String) -> Unit, numeric: Boolean = false) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions =
            KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
    )
}

@Composable
fun SelectField(
    label: String,
    choices: List<Pair<String, String>>,
    value: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            choices.find { it.first == value }?.second.orEmpty(),
            {},
            readOnly = true,
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onChange(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun DateField(value: String, label: String, withTime: Boolean, enabled: Boolean = true, onChange: (String) -> Unit) {
    val context = LocalContext.current
    fun choose() {
        val date = runCatching { LocalDate.parse(value.take(10)) }.getOrDefault(LocalDate.now())
        DatePickerDialog(
                context,
                { _, year, month, day ->
                    val selected = LocalDate.of(year, month + 1, day)
                    if (withTime) {
                        val time =
                            runCatching { LocalTime.parse(value.takeLast(5)) }
                                .getOrDefault(LocalTime.of(14, 0))
                        TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    onChange(
                                        "$selected ${LocalTime.of(hour,minute).format(DateTimeFormatter.ofPattern("HH:mm"))}"
                                    )
                                },
                                time.hour,
                                time.minute,
                                true,
                            )
                            .show()
                    } else onChange(selected.toString())
                },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            )
            .show()
    }
    Box {
        OutlinedTextField(
            value,
            {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = {
                IconButton(onClick = { choose() }, enabled = enabled) { Icon(Icons.Outlined.CalendarMonth, "选择日期与时间") }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
fun TournamentForm(
    original: Tournament?,
    rulesEditable: Boolean,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    serverError: String? = null,
    onSave: (Map<String, Any>) -> Unit,
) {
    val readOnly = original?.status == "ARCHIVED"
    var name by rememberSaveable(original?.id) { mutableStateOf(original?.name.orEmpty()) }
    var venue by rememberSaveable(original?.id) { mutableStateOf(original?.location.orEmpty()) }
    var date by rememberSaveable(original?.id) { mutableStateOf(original?.date ?: LocalDate.now().toString()) }
    var description by rememberSaveable(original?.id) { mutableStateOf(original?.description.orEmpty()) }
    var target by rememberSaveable(original?.id) { mutableStateOf((original?.rules?.target ?: 21).toString()) }
    var cap by rememberSaveable(original?.id) { mutableStateOf((original?.rules?.cap ?: 30).toString()) }
    var wins by rememberSaveable(original?.id) { mutableStateOf((original?.rules?.gamesToWin ?: 2).toString()) }
    var lead by rememberSaveable(original?.id) { mutableStateOf((original?.rules?.lead ?: 2).toString()) }
    var attempted by rememberSaveable(original?.id) { mutableStateOf(false) }
    val ruleErrors = if (attempted && rulesEditable) organizerRuleErrors(target, cap, wins, lead) else emptyMap()
    OrganizerFormDialog(
        if (readOnly) "赛事资料（只读）" else if (original == null) "创建赛事" else "赛事设置",
        onDismiss, busy = busy, error = serverError, readOnly = readOnly,
        onConfirm = {
            attempted = true
            if (!readOnly && name.isNotBlank() && venue.isNotBlank() &&
                (!rulesEditable || organizerRuleErrors(target, cap, wins, lead).isEmpty())) {
                val body = mutableMapOf<String, Any>(
                    "name" to name.trim(), "location" to venue.trim(),
                    "date" to date, "description" to description.trim(),
                )
                if (rulesEditable) body["rules"] = Rules().apply {
                    this.target = target.toInt(); this.cap = cap.toInt()
                    gamesToWin = wins.toInt(); this.lead = lead.toInt()
                }
                onSave(body)
            }
        },
    ) {
        if (readOnly) {
            Text("赛事已归档，资料仅供查询。", color = Lime)
            Text("赛事名称：$name")
            Text("举办地点：$venue")
            Text("举办日期：$date")
            Text("赛事说明：${description.ifBlank { "无" }}")
        } else {
            OrganizerInput(name, "赛事名称", { name = it }, !busy,
                error = if (attempted && name.isBlank()) "请填写赛事名称" else null)
            OrganizerInput(venue, "举办地点", { venue = it }, !busy,
                error = if (attempted && venue.isBlank()) "请填写举办地点" else null)
            DateField(date, "举办日期", false, enabled = !busy) { date = it }
            OrganizerInput(description, "赛事说明", { description = it }, !busy)
        }
        Text("比赛规则", fontWeight = FontWeight.Bold)
        if (rulesEditable && !readOnly) {
            OrganizerInput(target, "每局目标分", { target = it }, !busy, true, ruleErrors["target"])
            OrganizerInput(cap, "封顶分", { cap = it }, !busy, true, ruleErrors["cap"])
            OrganizerInput(wins, "获胜所需局数", { wins = it }, !busy, true, ruleErrors["wins"])
            OrganizerInput(lead, "最小领先分", { lead = it }, !busy, true, ruleErrors["lead"])
        } else {
            Text("${original?.rules?.gamesToWin}局获胜 · ${original?.rules?.target}分制 · 领先${original?.rules?.lead}分 · ${original?.rules?.cap}分封顶")
            if (!readOnly) Text("已有比赛发布，规则已锁定。", color = Soft)
        }
    }
}

@Composable
fun MatchForm(
    w: Workspace,
    original: Match?,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    serverError: String? = null,
    onSave: (Map<String, Any>) -> Unit,
) {
    var a by rememberSaveable(w.tournament.id, original?.id) {
        mutableStateOf(original?.sideAId ?: w.entrants.firstOrNull()?.id.orEmpty())
    }
    var b by rememberSaveable(w.tournament.id, original?.id) { mutableStateOf(original?.sideBId ?: w.entrants.getOrNull(1)?.id.orEmpty()) }
    var court by rememberSaveable(w.tournament.id, original?.id) {
        mutableStateOf(original?.courtId ?: w.courts.firstOrNull()?.id.orEmpty())
    }
    var ref by rememberSaveable(w.tournament.id, original?.id) {
        mutableStateOf(original?.refereeId ?: w.referees.firstOrNull()?.id.orEmpty())
    }
    var round by rememberSaveable(w.tournament.id, original?.id) { mutableStateOf(original?.round ?: "单打 · 第一轮") }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    fun local(value: String) =
        Instant.parse(value).atZone(ZoneId.of("Asia/Shanghai")).format(formatter)
    var start by rememberSaveable(w.tournament.id, original?.id) {
        mutableStateOf(original?.startsAt?.let(::local) ?: "${w.tournament.date} 14:00")
    }
    var end by rememberSaveable(w.tournament.id, original?.id) {
        mutableStateOf(original?.endsAt?.let(::local) ?: "${w.tournament.date} 14:45")
    }
    var error by rememberSaveable(w.tournament.id, original?.id) { mutableStateOf("") }
    OrganizerFormDialog(
        if (original == null) "编排新比赛" else "调整赛程",
        onDismiss,
        busy = busy, error = serverError,
        enabled = w.tournament.status != "ARCHIVED" && w.entrants.size >= 2 && w.courts.isNotEmpty() && w.referees.isNotEmpty(),
        onConfirm = {
            runCatching {
                    require(listOf(a, b, court, ref).all { it.isNotBlank() }) { "请先添加参赛者、场地与裁判" }
                    require(a != b) { "对阵双方不能相同" }
                    val s =
                        LocalDateTime.parse(start, formatter)
                            .atZone(ZoneId.of("Asia/Shanghai"))
                            .toInstant()
                    val e =
                        LocalDateTime.parse(end, formatter)
                            .atZone(ZoneId.of("Asia/Shanghai"))
                            .toInstant()
                    require(e > s) { "结束时间应晚于开始时间" }
                    onSave(
                        mapOf(
                            "sideAId" to a,
                            "sideBId" to b,
                            "courtId" to court,
                            "refereeId" to ref,
                            "round" to round,
                            "startsAt" to s.toString(),
                            "endsAt" to e.toString(),
                            "scheduleVersion" to (original?.scheduleVersion ?: 1),
                        )
                    )
                }
                .onFailure { error = it.message ?: "请检查输入" }
        },
    ) {
        if (w.entrants.size < 2 || w.courts.isEmpty() || w.referees.isEmpty())
            Text("需要至少两名参赛者、一块场地和一名裁判。请先在赛事资料中添加。", color = Rose, fontSize = 12.sp)
        OrganizerInput(round, "轮次或场次名称", { round = it }, !busy)
        OrganizerSelectField("蓝方", w.entrants.map { it.id to entrantLabel(it) }, a, !busy) { a = it }
        OrganizerSelectField("红方", w.entrants.map { it.id to entrantLabel(it) }, b, !busy) { b = it }
        OrganizerSelectField("场地", w.courts.map { it.id to it.name }, court, !busy) { court = it }
        OrganizerSelectField("执裁裁判", w.referees.map { it.id to "${it.name} · ${it.username}" }, ref, !busy) { ref = it }
        DateField(start, "预计开始时间", true, enabled = !busy) { start = it }
        DateField(end, "预计结束时间", true, enabled = !busy) { end = it }
        Text("云端会检查场地、裁判和参赛者的时间冲突。发布后的调整需要裁判重新接受。", color = Soft, fontSize = 11.sp)
        if (error.isNotBlank()) Text(error, color = Rose, fontSize = 12.sp)
    }
}

@Composable
fun ReasonDialog(
    title: String,
    detail: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    FormDialog(title, onDismiss, enabled = reason.isNotBlank(), onConfirm = { onConfirm(reason) }) {
        Text(detail, color = Soft, fontSize = 12.sp)
        Input(reason, "原因", { reason = it })
    }
}

@Composable
fun ManagementScreen(state: UiState, vm: CourtsideViewModel, onSettings: () -> Unit) {
    val w = state.workspace ?: return
    var tab by rememberSaveable(w.tournament.id) { mutableStateOf("参赛者") }
    var editKind by rememberSaveable(w.tournament.id) { mutableStateOf("") }
    var editId by rememberSaveable(w.tournament.id) { mutableStateOf<String?>(null) }
    var name by rememberSaveable(w.tournament.id) { mutableStateOf("") }
    var unit by rememberSaveable(w.tournament.id) { mutableStateOf("") }
    var archive by rememberSaveable(w.tournament.id) { mutableStateOf(false) }
    var account by rememberSaveable(w.tournament.id) { mutableStateOf(false) }
    var username by rememberSaveable(w.tournament.id) { mutableStateOf("") }
    var password by rememberSaveable(w.tournament.id) { mutableStateOf("") }
    val pendingReferee = state.pendingRefereeJoins[w.tournament.id]
    LaunchedEffect(pendingReferee?.id) {
        if (pendingReferee != null) password = ""
    }
    val archiveReason = archiveBlockReason(w)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Panel {
                Text(w.tournament.name, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${w.tournament.date} · ${w.tournament.location}",
                    color = Soft,
                    fontSize = 12.sp,
                )
                OutlinedButton(onClick = onSettings, enabled = !state.busy) { Text(if (w.tournament.status == "ARCHIVED") "查看赛事资料" else "赛事设置") }
            }
        }
        item { FilterRow(listOf("参赛者", "场地", "裁判"), tab) { tab = it } }
        if (tab == "参赛者") {
            item {
                PrimaryButton(
                    "添加参赛者",
                    {
                        vm.clearOrganizerError()
                        editKind = "entrants"
                        editId = null
                        name = ""
                        unit = ""
                    },
                    Modifier.fillMaxWidth(),
                    w.tournament.status != "ARCHIVED" && !state.busy,
                    Icons.Outlined.PersonAddAlt,
                )
            }
            items(w.entrants, key = { it.id }) { e ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("${e.unit} · 编号 ${e.id.take(8)}", color = Soft, fontSize = 14.sp)
                        }
                        ActionIcon(
                            Icons.Outlined.Edit,
                            "编辑 ${e.name}",
                            {
                                vm.clearOrganizerError()
                                editKind = "entrants"
                                editId = e.id
                                name = e.name
                                unit = e.unit
                            },
                            w.tournament.status != "ARCHIVED" && !state.busy,
                        )
                    }
                }
            }
        } else if (tab == "场地") {
            item {
                PrimaryButton(
                    "添加场地",
                    {
                        vm.clearOrganizerError()
                        editKind = "courts"
                        editId = null
                        name = ""
                    },
                    Modifier.fillMaxWidth(),
                    w.tournament.status != "ARCHIVED" && !state.busy,
                    Icons.Outlined.AddLocationAlt,
                )
            }
            items(w.courts, key = { it.id }) { c ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.GridOn, null, tint = Lime)
                        Spacer(Modifier.width(14.dp))
                        Text(c.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        ActionIcon(
                            Icons.Outlined.Edit,
                            "编辑场地",
                            {
                                vm.clearOrganizerError()
                                editKind = "courts"
                                editId = c.id
                                name = c.name
                            },
                            w.tournament.status != "ARCHIVED" && !state.busy,
                        )
                    }
                }
            }
        } else {
            item {
                PrimaryButton(
                    if (pendingReferee == null) "创建裁判账号" else "加入已创建的裁判",
                    {
                        vm.clearOrganizerError()
                        account = true
                        name = ""
                        username = ""
                        password = ""
                    },
                    Modifier.fillMaxWidth(),
                    w.tournament.status != "ARCHIVED" && !state.busy,
                    Icons.Outlined.PersonAddAlt,
                )
            }
            item { Text("已加入 ${w.referees.size} 位裁判", fontWeight = FontWeight.Bold) }
            items(state.referees, key = { it.id }) { ref ->
                val joined = w.referees.any { it.id == ref.id }
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ref.name, fontWeight = FontWeight.Bold)
                            Text(ref.username, color = Soft, fontSize = 12.sp)
                        }
                        if (joined) Text("已加入", color = Lime, fontSize = 12.sp)
                        else
                            OutlinedButton(
                                onClick = { vm.resource("members", mapOf("userId" to ref.id)) },
                                enabled = w.tournament.status != "ARCHIVED" && !state.busy,
                            ) {
                                Text("加入赛事")
                            }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { vm.clearOrganizerError(); archive = true },
                enabled =
                    w.tournament.status != "ARCHIVED" && !state.busy &&
                        w.matches.isNotEmpty() &&
                        w.matches.all { it.status in listOf("FINISHED", "CANCELLED") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (w.tournament.status == "ARCHIVED") "赛事已归档" else "归档赛事")
            }
            Text(archiveReason ?: "全部场次已完成或取消，可以归档保存。", color = Soft, fontSize = 14.sp)
        }
    }
    if (editKind.isNotBlank())
        OrganizerFormDialog(
            if (editId == null) "添加${if(editKind=="entrants")"参赛者" else "场地"}" else "编辑资料",
            { editKind = "" },
            enabled = name.isNotBlank(), busy = state.busy, error = state.organizerError,
            onConfirm = {
                vm.resource(editKind, mapOf("name" to name.trim(), "unit" to unit.trim()), editId) { editKind = "" }
            },
        ) {
            OrganizerInput(name, "名称", { name = it }, !state.busy)
            if (editKind == "entrants") OrganizerInput(unit, "所属单位", { unit = it }, !state.busy)
        }
    if (archive)
        AlertDialog(
            onDismissRequest = { if (!state.busy) archive = false },
            title = { Text("归档赛事") },
            text = { Column { Text("归档后赛事和比赛资料将只供查询。")
                state.organizerError?.let { Text(it, color = Rose) } } },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.editTournament(mapOf("status" to "ARCHIVED")) { archive = false }
                    }, enabled = !state.busy
                ) {
                    Text(if (state.busy) "正在归档…" else "确认归档")
                }
            },
            dismissButton = { TextButton(onClick = { archive = false }, enabled = !state.busy) { Text("返回") } },
        )
    if (account)
        OrganizerFormDialog(
            if (pendingReferee == null) "创建并加入裁判" else "加入已创建的裁判",
            { account = false },
            confirmLabel = if (pendingReferee == null) "保存" else "重试加入赛事",
            enabled = w.tournament.status != "ARCHIVED" && (pendingReferee != null ||
                name.isNotBlank() && refereeAccountError(username, password) == null),
            busy = state.busy, error = state.organizerError,
            onConfirm = {
                if (pendingReferee == null) {
                    vm.createReferee(username, name.trim(), password) { account = false; password = "" }
                } else {
                    vm.retryRefereeJoin(w.tournament.id) { account = false; password = "" }
                }
            },
        ) {
            if (pendingReferee != null) {
                Text("裁判账号 ${pendingReferee.username} 已创建。", fontWeight = FontWeight.Bold)
                Text("尚未确认加入当前赛事，可重试加入。", color = Soft)
            } else {
                OrganizerInput(name, "裁判姓名", { name = it }, !state.busy)
                OrganizerInput(username, "登录账号（3～40位）", { username = it }, !state.busy,
                    error = if (username.isNotEmpty() && !username.matches(Regex("[a-zA-Z0-9_.-]{3,40}"))) "仅支持字母、数字、下划线、点、短横线，共3～40位" else null)
                val invalidPassword = password.isNotEmpty() && (password.length !in 10..72 || password != password.trim())
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("初始密码（10～72位）") },
                    enabled = !state.busy,
                    isError = invalidPassword,
                    supportingText = { if (invalidPassword) Text("密码需为10～72位，首尾不能有空白") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("创建后将自动加入当前赛事，请将账号交给裁判本人。", color = Soft, fontSize = 11.sp)
            }
        }
}

@Composable
fun TossDialog(m: Match, onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    var drawn by remember { mutableIntStateOf(-1) }
    var server by remember { mutableIntStateOf(0) }
    FormDialog("赛前准备", onDismiss, "确认并开始比赛", onConfirm = { onStart(server) }) {
        Text("核对对阵与场地，然后决定初始发球方。", color = Soft, fontSize = 12.sp)
        Text("${m.sideA}  VS  ${m.sideB}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        OutlinedButton(
            onClick = { drawn = Random.nextInt(2) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Casino, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("随机决定优先选择方")
        }
        if (drawn >= 0)
            Text(
                "${if(drawn==0)m.sideA else m.sideB} 优先选择",
                color = Lime,
                fontWeight = FontWeight.Bold,
            )
        Text("优先方可选择先发球、先接发球或场区；根据现场选择设置下方发球方。", color = Soft, fontSize = 12.sp)
        SelectField("本局首先发球", listOf("0" to m.sideA, "1" to m.sideB), server.toString()) {
            server = it.toInt()
        }
        Text("开始需连接云端；开始后会锁定比赛安排并在本机保存计分。", color = Soft, fontSize = 11.sp)
    }
}

@Composable
fun ReviewDialog(
    s: Submission,
    m: Match?,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    serverError: String? = null,
    readOnly: Boolean = false,
    onReview: (Boolean, String) -> Unit,
) {
    if (m == null) return
    var returning by rememberSaveable(s.id) { mutableStateOf(false) }
    var reason by rememberSaveable(s.id) { mutableStateOf("") }
    val pending = s.status == "PENDING_REVIEW" && !readOnly
    val title = when {
        pending -> "核验比赛成绩"
        s.status == "CONFIRMED" -> "正式成绩"
        s.status == "RETURNED" -> "已退回的成绩"
        else -> "提交详情"
    }
    OrganizerFormDialog(
        title, onDismiss,
        confirmLabel = if (pending) "确认正式成绩" else "完成",
        busy = busy, error = serverError,
        onConfirm = { if (pending) onReview(true, "") else onDismiss() },
    ) {
        OrganizerPlayers(m)
        if (readOnly) OrganizerSubmissionRecordMetadata(s, m)
        Text("局胜 ${Scoring.wins(s.score, 0)} : ${Scoring.wins(s.score, 1)}", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("${if (s.winner == 0) m.sideA else m.sideB} 获胜", color = Lime)
        if (s.score.retirementWinner >= 0)
            Text("弃权记录：${s.score.retirementReason}", color = Rose, fontSize = 14.sp)
        s.score.games.forEachIndexed { i, g ->
            Text("第 ${i + 1} 局  ${g.a} – ${g.b}", fontWeight = FontWeight.Bold)
            RallyGrid(g.rallies)
            if (g.corrected) Text("局分更正：${g.correctionNote}", color = Rose, fontSize = 14.sp)
        }
        if (s.score.rallies.isNotEmpty()) {
            Text("第 ${s.score.games.size + 1} 局（未封存） ${Scoring.points(s.score.rallies, 0)} – ${Scoring.points(s.score.rallies, 1)}",
                fontWeight = FontWeight.Bold)
            RallyGrid(s.score.rallies)
        }
        Text("双方确认：${if (s.score.aConfirmed) "蓝方已确认" else "蓝方未确认"} / ${if (s.score.bConfirmed) "红方已确认" else "红方未确认"}", color = Soft)
        Text("裁判 ${m.refereeName} · 回执 ${s.id.take(8)}", color = Soft, fontSize = 14.sp)
        if (pending) OutlinedButton(onClick = { returning = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("退回更正", color = Rose)
        }
    }
    if (pending && returning) OrganizerReasonDialog(
        "退回裁判更正", "原始提交会保留，裁判将收到原因并重新确认成绩。",
        busy, serverError, { returning = false }, reason, { reason = it },
    ) { onReview(false, it) }
}
