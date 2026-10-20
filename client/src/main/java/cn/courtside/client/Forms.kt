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
fun DateField(value: String, label: String, withTime: Boolean, onChange: (String) -> Unit) {
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
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = {
                IconButton(onClick = { choose() }) { Icon(Icons.Outlined.CalendarMonth, "选择日期与时间") }
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
    onSave: (Map<String, Any>) -> Unit,
) {
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var venue by remember { mutableStateOf(original?.location.orEmpty()) }
    var date by remember { mutableStateOf(original?.date ?: LocalDate.now().toString()) }
    var description by remember { mutableStateOf(original?.description.orEmpty()) }
    var target by remember { mutableStateOf((original?.rules?.target ?: 21).toString()) }
    var cap by remember { mutableStateOf((original?.rules?.cap ?: 30).toString()) }
    var wins by remember { mutableStateOf((original?.rules?.gamesToWin ?: 2).toString()) }
    var lead by remember { mutableStateOf((original?.rules?.lead ?: 2).toString()) }
    var error by remember { mutableStateOf("") }
    FormDialog(
        if (original == null) "创建赛事" else "赛事设置",
        onDismiss,
        onConfirm = {
            runCatching {
                    require(name.isNotBlank() && venue.isNotBlank()) { "请填写名称和地点" }
                    val body =
                        mutableMapOf<String, Any>(
                            "name" to name,
                            "location" to venue,
                            "date" to date,
                            "description" to description,
                        )
                    if (rulesEditable) {
                        val r =
                            Rules().apply {
                                this.target = target.toInt()
                                this.cap = cap.toInt()
                                gamesToWin = wins.toInt()
                                this.lead = lead.toInt()
                            }
                        Scoring.validateRules(r)
                        body["rules"] = r
                    }
                    onSave(body)
                }
                .onFailure { error = it.message ?: "请输入有效规则" }
        },
    ) {
        Input(name, "赛事名称", { name = it })
        Input(venue, "举办地点", { venue = it })
        DateField(date, "举办日期", false) { date = it }
        Input(description, "赛事说明", { description = it })
        Text("比赛规则", fontWeight = FontWeight.Bold)
        if (rulesEditable) {
            Input(target, "每局目标分", { target = it }, true)
            Input(cap, "封顶分", { cap = it }, true)
            Input(wins, "获胜所需局数", { wins = it }, true)
            Input(lead, "最小领先分", { lead = it }, true)
        } else Text("已发布比赛，规则已锁定。${original?.rules?.target} 分制。", color = Soft, fontSize = 12.sp)
        if (error.isNotBlank()) Text(error, color = Rose, fontSize = 12.sp)
    }
}

@Composable
fun MatchForm(
    w: Workspace,
    original: Match?,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any>) -> Unit,
) {
    var a by remember {
        mutableStateOf(original?.sideAId ?: w.entrants.firstOrNull()?.id.orEmpty())
    }
    var b by remember { mutableStateOf(original?.sideBId ?: w.entrants.getOrNull(1)?.id.orEmpty()) }
    var court by remember {
        mutableStateOf(original?.courtId ?: w.courts.firstOrNull()?.id.orEmpty())
    }
    var ref by remember {
        mutableStateOf(original?.refereeId ?: w.referees.firstOrNull()?.id.orEmpty())
    }
    var round by remember { mutableStateOf(original?.round ?: "单打 · 第一轮") }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    fun local(value: String) =
        Instant.parse(value).atZone(ZoneId.of("Asia/Shanghai")).format(formatter)
    var start by remember {
        mutableStateOf(original?.startsAt?.let(::local) ?: "${w.tournament.date} 14:00")
    }
    var end by remember {
        mutableStateOf(original?.endsAt?.let(::local) ?: "${w.tournament.date} 14:45")
    }
    var error by remember { mutableStateOf("") }
    FormDialog(
        if (original == null) "编排新比赛" else "调整赛程",
        onDismiss,
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
        Input(round, "轮次或场次名称", { round = it })
        SelectField("蓝方", w.entrants.map { it.id to "${it.name} · ${it.unit}" }, a) { a = it }
        SelectField("红方", w.entrants.map { it.id to "${it.name} · ${it.unit}" }, b) { b = it }
        SelectField("场地", w.courts.map { it.id to it.name }, court) { court = it }
        SelectField("执裁裁判", w.referees.map { it.id to it.name }, ref) { ref = it }
        DateField(start, "预计开始时间", true) { start = it }
        DateField(end, "预计结束时间", true) { end = it }
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
    var tab by remember { mutableStateOf("参赛者") }
    var editKind by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var archive by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
                OutlinedButton(onClick = onSettings) { Text("赛事设置") }
            }
        }
        item { FilterRow(listOf("参赛者", "场地", "裁判"), tab) { tab = it } }
        if (tab == "参赛者") {
            item {
                PrimaryButton(
                    "添加参赛者",
                    {
                        editKind = "entrants"
                        editId = null
                        name = ""
                        unit = ""
                    },
                    Modifier.fillMaxWidth(),
                    w.tournament.status != "ARCHIVED",
                    Icons.Outlined.PersonAddAlt,
                )
            }
            items(w.entrants, key = { it.id }) { e ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(e.unit, color = Soft, fontSize = 12.sp)
                        }
                        ActionIcon(
                            Icons.Outlined.Edit,
                            "编辑 ${e.name}",
                            {
                                editKind = "entrants"
                                editId = e.id
                                name = e.name
                                unit = e.unit
                            },
                            w.tournament.status != "ARCHIVED",
                        )
                    }
                }
            }
        } else if (tab == "场地") {
            item {
                PrimaryButton(
                    "添加场地",
                    {
                        editKind = "courts"
                        editId = null
                        name = ""
                    },
                    Modifier.fillMaxWidth(),
                    w.tournament.status != "ARCHIVED",
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
                                editKind = "courts"
                                editId = c.id
                                name = c.name
                            },
                            w.tournament.status != "ARCHIVED",
                        )
                    }
                }
            }
        } else {
            item {
                PrimaryButton(
                    "创建裁判账号",
                    {
                        account = true
                        name = ""
                        username = ""
                        password = ""
                    },
                    Modifier.fillMaxWidth(),
                    w.tournament.status != "ARCHIVED",
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
                                enabled = w.tournament.status != "ARCHIVED",
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
                onClick = { archive = true },
                enabled =
                    w.tournament.status != "ARCHIVED" &&
                        w.matches.isNotEmpty() &&
                        w.matches.all { it.status in listOf("FINISHED", "CANCELLED") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (w.tournament.status == "ARCHIVED") "赛事已归档" else "归档赛事")
            }
            Text("全部场次确认完成或取消后，可以归档保存。", color = Soft, fontSize = 11.sp)
        }
    }
    if (editKind.isNotBlank())
        FormDialog(
            if (editId == null) "添加${if(editKind=="entrants")"参赛者" else "场地"}" else "编辑资料",
            { editKind = "" },
            enabled = name.isNotBlank(),
            onConfirm = {
                vm.resource(editKind, mapOf("name" to name, "unit" to unit), editId)
                editKind = ""
            },
        ) {
            Input(name, "名称", { name = it })
            if (editKind == "entrants") Input(unit, "所属单位", { unit = it })
        }
    if (archive)
        AlertDialog(
            onDismissRequest = { archive = false },
            title = { Text("归档赛事") },
            text = { Text("归档后赛事和比赛资料将只供查询。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.editTournament(mapOf("status" to "ARCHIVED"))
                        archive = false
                    }
                ) {
                    Text("确认归档")
                }
            },
            dismissButton = { TextButton(onClick = { archive = false }) { Text("返回") } },
        )
    if (account)
        FormDialog(
            "创建并加入裁判",
            { account = false },
            enabled = name.isNotBlank() && username.length >= 3 && password.length >= 10,
            onConfirm = {
                vm.createReferee(username, name, password)
                account = false
            },
        ) {
            Input(name, "裁判姓名", { name = it })
            Input(username, "登录账号（字母与数字）", { username = it })
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("初始密码（至少10位）") },
                visualTransformation =
                    androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text("创建后将自动加入当前赛事，请将账号交给裁判本人。", color = Soft, fontSize = 11.sp)
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
    onReview: (Boolean, String) -> Unit,
) {
    if (m == null) return
    var returning by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PanelColor,
            modifier = Modifier.heightIn(max = 650.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    if (s.status == "PENDING_REVIEW") "核验比赛成绩" else "正式成绩",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${m.sideA}   ${Scoring.wins(s.score,0)} : ${Scoring.wins(s.score,1)}   ${m.sideB}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                )
                Column(
                    Modifier.weight(1f, false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("${if(s.winner==0)m.sideA else m.sideB} 获胜", color = Lime)
                    if (s.score.retirementWinner >= 0)
                        Text("弃权记录：${s.score.retirementReason}", color = Rose, fontSize = 12.sp)
                    s.score.games.forEachIndexed { i, g ->
                        Text("第 ${i+1} 局  ${g.a} – ${g.b}", fontWeight = FontWeight.Bold)
                        RallyGrid(g.rallies)
                        if (g.corrected)
                            Text("局分更正：${g.correctionNote}", color = Rose, fontSize = 11.sp)
                    }
                    Text(
                        "双方确认：${if(s.score.aConfirmed)"蓝方已确认" else "蓝方未确认"} / ${if(s.score.bConfirmed)"红方已确认" else "红方未确认"}",
                        color = Soft,
                        fontSize = 11.sp,
                    )
                    Text("裁判 ${m.refereeName} · 回执 ${s.id.take(8)}", color = Soft, fontSize = 10.sp)
                }
                if (s.status == "PENDING_REVIEW") {
                    PrimaryButton("确认正式成绩", { onReview(true, "") }, Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = { returning = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("退回更正", color = Rose)
                    }
                } else PrimaryButton("完成", onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
    if (returning)
        ReasonDialog("退回裁判更正", "原始提交会保留，裁判将收到原因并重新确认成绩。", { returning = false }) {
            onReview(false, it)
        }
}
