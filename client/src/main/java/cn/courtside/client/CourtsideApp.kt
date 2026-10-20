@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package cn.courtside.client

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.courtside.shared.Models.*
import cn.courtside.shared.Scoring

@Composable
fun CourtsideApp(organizer: Boolean, vm: CourtsideViewModel = viewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(state.route) { if (state.route == "score") snack.currentSnackbarData?.dismiss() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAccount by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var editedMatch by remember { mutableStateOf<Match?>(null) }
    var reviewed by remember { mutableStateOf<Submission?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snack.showSnackbar(it)
            vm.dismissMessage()
        }
    }
    BackHandler(enabled = state.login != null && state.route != "home") { vm.navigate("home") }
    CourtsideTheme {
        if (state.login == null) {
            LoginScreen(
                organizer,
                state.busy,
                vm.repo.baseUrl,
                { url, name, password -> vm.signIn(url, name, password, organizer) },
                state.message,
            )
            return@CourtsideTheme
        }
        Scaffold(
            containerColor = Ink,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (state.route == "home")
                    NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
                        val titles =
                            if (organizer) listOf("赛事总览", "赛程", "成绩")
                            else listOf("工作台", "我的赛程", "执裁记录")
                        val icons =
                            listOf(
                                Icons.Outlined.Dashboard,
                                Icons.Outlined.CalendarMonth,
                                Icons.Outlined.EmojiEvents,
                            )
                        titles.forEachIndexed { i, title ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(icons[i], title, Modifier.size(22.dp)) },
                                label = { Text(title, fontSize = 11.sp) },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        indicatorColor = Lime.copy(alpha = .14f),
                                        selectedIconColor = Lime,
                                        selectedTextColor = Lime,
                                        unselectedIconColor = Soft,
                                        unselectedTextColor = Soft,
                                    ),
                            )
                        }
                    }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.busy)
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().height(2.dp),
                        color = Lime,
                        trackColor = Edge,
                    )
                if (state.route != "score") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.route != "home")
                            ActionIcon(Icons.Outlined.ArrowBack, "返回工作台", { vm.navigate("home") })
                        else
                            Box(
                                Modifier.size(32.dp)
                                    .background(
                                        Lime,
                                        CutCornerShape(topEnd = 10.dp, bottomStart = 10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.SportsTennis,
                                    null,
                                    tint = Ink,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.route == "manage") "赛事资料"
                                else if (state.route == "detail") "比赛详情"
                                else if (state.route == "result") "赛后回顾" else "羽序 COURTSIDE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Eyebrow(if (organizer) "TOURNAMENT CONTROL" else "REFEREE CONSOLE")
                        }
                        ActionIcon(Icons.Outlined.Sync, "刷新云端数据", { vm.refreshNow() })
                        ActionIcon(Icons.Outlined.AccountCircle, "账户与连接", { showAccount = true })
                    }
                    if (!state.online)
                        Row(
                            Modifier.fillMaxWidth()
                                .background(Rose.copy(alpha = .12f))
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.CloudOff, null, Modifier.size(15.dp), tint = Rose)
                            Spacer(Modifier.width(8.dp))
                            Text("当前离线 · 已开始比赛可继续计分", color = Rose, fontSize = 12.sp)
                        }
                }
                when (state.route) {
                    "score" -> ScoreScreen(state, vm)
                    "result" -> ResultScreen(state, vm)
                    "manage" -> ManagementScreen(state, vm, { dialog = "settings" })
                    "detail" ->
                        MatchDetail(
                            state,
                            organizer,
                            vm,
                            {
                                editedMatch = it
                                dialog = "match"
                            },
                            { reviewed = it },
                        )
                    else ->
                        if (organizer)
                            OrganizerHome(
                                state,
                                tab,
                                vm,
                                { dialog = "tournament" },
                                {
                                    editedMatch = null
                                    dialog = "match"
                                },
                                { reviewed = it },
                            )
                        else RefereeHome(state, tab, vm) { tab = 1 }
                }
            }
        }
        if (showAccount)
            AlertDialog(
                onDismissRequest = { showAccount = false },
                icon = { Icon(Icons.Outlined.AccountCircle, null, tint = Lime) },
                title = { Text(state.login!!.user.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (organizer) "赛事组客户端" else "裁判客户端", color = Soft)
                        Text(vm.repo.baseUrl, fontSize = 12.sp)
                        Text("最后同步 ${state.lastSync} · 待提交 ${state.pending} 场", fontSize = 12.sp)
                        Text("退出后，本机计分记录仍为原账号保留。", color = Soft, fontSize = 12.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showAccount = false }) { Text("继续使用") } },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAccount = false
                            vm.logout()
                        }
                    ) {
                        Text("退出登录", color = Rose)
                    }
                },
            )
        if (dialog == "tournament")
            TournamentForm(null, true, { dialog = "" }) {
                vm.createTournament(it)
                dialog = ""
            }
        if (dialog == "settings")
            state.workspace?.let { w ->
                TournamentForm(
                    w.tournament,
                    w.matches.all { it.status == "DRAFT" },
                    { dialog = "" },
                ) {
                    vm.editTournament(it)
                    dialog = ""
                }
            }
        if (dialog == "match")
            state.workspace?.let { w ->
                MatchForm(w, editedMatch, { dialog = "" }) { body ->
                    vm.saveMatch(body, editedMatch?.id)
                    dialog = ""
                }
            }
        reviewed?.let { s ->
            ReviewDialog(
                s,
                state.workspace?.matches?.find { it.id == s.matchId },
                { reviewed = null },
                { yes, reason ->
                    vm.review(s, yes, reason)
                    reviewed = null
                },
            )
        }
    }
}

@Composable
private fun LoginScreen(
    organizer: Boolean,
    busy: Boolean,
    url: String,
    onLogin: (String, String, String) -> Unit,
    error: String?,
) {
    var username by
        rememberSaveable(organizer) { mutableStateOf(if (organizer) "organizer" else "referee") }
    var password by rememberSaveable { mutableStateOf("Courtside2026!") }
    var address by rememberSaveable { mutableStateOf(url) }
    var connection by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(Ink).safeDrawingPadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SportsTennis, null, Modifier.size(28.dp), tint = Lime)
                Spacer(Modifier.width(10.dp))
                Text("羽序", fontWeight = FontWeight.Black, fontSize = 23.sp)
                Spacer(Modifier.weight(1f))
                Eyebrow("COURTSIDE / 01", Lime)
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                CourtArt(Modifier.fillMaxSize(), true)
                Column(
                    Modifier.align(Alignment.CenterStart),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Eyebrow(if (organizer) "RUN THE TOURNAMENT" else "OWN THE COURT", Lime)
                    Text(
                        if (organizer) "组织有序\n精彩上场" else "专注球场\n掌握每一分",
                        fontSize = 38.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (organizer) "从第一场安排，到最后一分确认。" else "赛程、计分与回顾，在这里衔接。",
                        fontSize = 12.sp,
                        color = Soft,
                    )
                }
            }
        }
        item {
            Panel {
                Text(
                    if (organizer) "赛事组登录" else "裁判登录",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("使用赛事组提供的账号进入工作台", color = Soft, fontSize = 12.sp)
                OutlinedTextField(
                    username,
                    { username = it },
                    label = { Text("账号") },
                    leadingIcon = { Icon(Icons.Outlined.PersonOutline, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                )
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Outlined.LockOpen, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                )
                if (!error.isNullOrBlank()) Text(error, color = Rose, fontSize = 12.sp)
                PrimaryButton(
                    if (busy) "正在连接…" else "进入${if(organizer)"赛事组" else "裁判"}工作台",
                    { onLogin(address, username, password) },
                    Modifier.fillMaxWidth(),
                    !busy && username.isNotBlank() && password.isNotBlank(),
                    Icons.Outlined.ArrowForward,
                )
            }
        }
        item {
            TextButton(onClick = { connection = !connection }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Dns, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("服务连接设置")
            }
            AnimatedVisibility(connection) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        address,
                        { address = it },
                        label = { Text("云服务地址") },
                        supportingText = { Text("模拟器：10.0.2.2；真机：填写电脑的局域网地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("本机示例账号已填入。连接自己的服务后，使用对应账号登录。", fontSize = 11.sp, color = Soft)
                }
            }
        }
        item {
            Text(
                "COURTSIDE  ·  羽毛球赛事与裁判协同",
                fontSize = 10.sp,
                color = Soft,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun SyncLine(state: UiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(5.dp).background(if (state.online) Lime else Rose, CircleShape))
        Text(
            if (state.online) "云端连接 · ${state.lastSync}" else "本机缓存 · ${state.lastSync}",
            color = Soft,
            fontSize = 10.sp,
        )
        if (state.pending > 0) Text("${state.pending} 场待同步", color = Rose, fontSize = 10.sp)
    }
}

@Composable
private fun Stats(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, (n, label) ->
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PanelColor)
                    .padding(horizontal = 14.dp, vertical = 15.dp)
            ) {
                Text(
                    n,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = if (i == 0) Lime else Paper,
                )
                Text(label, fontSize = 11.sp, color = Soft)
            }
        }
    }
}

@Composable
private fun RefereeHome(state: UiState, tab: Int, vm: CourtsideViewModel, onSchedule: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("全部") }
    val all = state.feed.matches
    val next =
        all.firstOrNull { it.status == "IN_PROGRESS" }
            ?: all.firstOrNull { it.status == "ACCEPTED" }
            ?: all.firstOrNull { it.status == "PUBLISHED" }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SyncLine(state) }
        if (tab == 0) {
            item {
                Column {
                    Text(
                        "${state.login?.user?.name?.substringAfter("·")?.trim()}，准备上场",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "赛前有安排，赛中有把握。",
                        color = Soft,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 228.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF1B2F36), Color(0xFF101C29)))
                        )
                ) {
                    CourtArt(Modifier.matchParentSize())
                    Column(
                        Modifier.padding(22.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Eyebrow("YOUR NEXT MATCH", Lime)
                        Text(next?.round ?: "今天的任务已完成", fontSize = 13.sp, color = Soft)
                        Text(
                            if (next != null) "${next.sideA}  /  ${next.sideB}" else "每一分，都已记录",
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.fillMaxWidth(.83f),
                        )
                        Text(
                            next?.let {
                                "${it.court}  ·  ${timeLabel(it.startsAt)}  ·  ${it.tournamentName}"
                            } ?: "新的赛程会在这里出现",
                            fontSize = 11.sp,
                            color = Soft,
                        )
                        if (next != null)
                            PrimaryButton(
                                if (next.status == "IN_PROGRESS") "继续这场比赛" else "查看比赛安排",
                                { vm.openMatch(next) },
                                icon = Icons.Outlined.ArrowForward,
                            )
                    }
                }
            }
            item {
                Stats(
                    listOf(
                        all.count { it.status == "PUBLISHED" }.toString() to "待接受",
                        all.count { it.status in listOf("ACCEPTED", "IN_PROGRESS") }.toString() to
                            "待执裁",
                        all.count { it.status == "FINISHED" }.toString() to "已完成",
                    )
                )
            }
            if (state.pending > 0)
                item {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudUpload, null, tint = Rose)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("有 ${state.pending} 场成绩等待同步", fontWeight = FontWeight.Bold)
                                Text("本地记录已保存，联网后自动重试", color = Soft, fontSize = 11.sp)
                            }
                            ActionIcon(Icons.Outlined.Refresh, "重试同步", { vm.refreshNow() })
                        }
                    }
                }
            item { SectionTitle("待办比赛", "全部赛程", onSchedule) }
            items(
                all.filter {
                        it.status in
                            listOf("PUBLISHED", "ACCEPTED", "IN_PROGRESS", "PENDING_REVIEW")
                    }
                    .take(6),
                key = { it.id },
            ) {
                MatchCard(it, { vm.openMatch(it) })
            }
            if (all.isEmpty()) item { EmptyState("暂时没有执裁任务", "赛事组发布安排后，任务会自动出现在这里。") }
        } else if (tab == 1) {
            item {
                Text("我的赛程", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("核对场地和时间，接受后准时到场。", fontSize = 12.sp, color = Soft)
            }
            item { FilterRow(listOf("全部", "待接受", "未开始", "进行中"), filter) { filter = it } }
            val shown =
                all.filter {
                    when (filter) {
                        "待接受" -> it.status == "PUBLISHED"
                        "未开始" -> it.status in listOf("PUBLISHED", "ACCEPTED")
                        "进行中" -> it.status == "IN_PROGRESS"
                        else -> true
                    }
                }
            shown
                .groupBy { dateLabel(it.startsAt) }
                .forEach { (date, matches) ->
                    item { SectionTitle(date) }
                    items(matches, key = { it.id }) { MatchCard(it, { vm.openMatch(it) }) }
                }
            if (shown.isEmpty()) item { EmptyState("这个分类暂无比赛", "可以切换分类，或刷新最新赛程。") }
        } else {
            item {
                Text("执裁记录", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("从第一球到赛后确认，都有迹可循。", fontSize = 12.sp, color = Soft)
            }
            items(
                all.filter {
                    it.status in listOf("FINISHED", "PENDING_REVIEW") || it.correctionRound > 0
                },
                key = { it.id },
            ) {
                MatchCard(it, { vm.openMatch(it) })
            }
            if (
                all.none {
                    it.status in listOf("FINISHED", "PENDING_REVIEW") || it.correctionRound > 0
                }
            )
                item { EmptyState("还没有完赛记录", "完成计分并提交后，可以在这里回顾。") }
        }
    }
}

@Composable
fun FilterRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach {
            FilterChip(
                selected == it,
                { onSelect(it) },
                label = { Text(it) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Lime,
                        selectedLabelColor = Ink,
                    ),
            )
        }
    }
}

@Composable
private fun OrganizerHome(
    state: UiState,
    tab: Int,
    vm: CourtsideViewModel,
    newTournament: () -> Unit,
    newMatch: () -> Unit,
    onReview: (Submission) -> Unit,
) {
    val w = state.workspace
    var filter by rememberSaveable { mutableStateOf("全部") }
    var choose by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SyncLine(state) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextButton(
                        onClick = { choose = true },
                        modifier = Modifier.semantics { contentDescription = "选择当前赛事" },
                    ) {
                        Text(
                            w?.tournament?.name ?: "选择赛事",
                            color = Paper,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Outlined.ExpandMore, null)
                    }
                    DropdownMenu(choose, { choose = false }) {
                        state.tournaments.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name) },
                                onClick = {
                                    choose = false
                                    vm.chooseTournament(t)
                                },
                            )
                        }
                    }
                }
                ActionIcon(Icons.Outlined.AddCircleOutline, "创建赛事", newTournament)
            }
        }
        if (w == null) {
            item { EmptyState("创建你的第一项赛事", "添加参赛者、场地与裁判，即可编排比赛。") }
            item {
                PrimaryButton(
                    "创建赛事",
                    newTournament,
                    Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Add,
                )
            }
        } else if (tab == 0) {
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(25.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF24382D), Color(0xFF142329)))
                        )
                ) {
                    CourtArt(Modifier.matchParentSize(), true)
                    Column(
                        Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Eyebrow("TOURNAMENT / ${w.tournament.date}", Lime)
                        Text(
                            w.tournament.name,
                            fontSize = 29.sp,
                            lineHeight = 37.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.fillMaxWidth(.87f),
                        )
                        Text(
                            "${w.tournament.location}  ·  ${if(w.tournament.status=="ARCHIVED")"已归档" else "赛事进行中"}",
                            fontSize = 12.sp,
                            color = Soft,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton(
                                "编排比赛",
                                newMatch,
                                enabled = w.tournament.status != "ARCHIVED",
                                icon = Icons.Outlined.Add,
                            )
                            OutlinedButton(
                                onClick = { vm.navigate("manage") },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("管理资料", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            item {
                Stats(
                    listOf(
                        w.matches.size.toString() to "比赛总数",
                        w.matches.count { it.status == "IN_PROGRESS" }.toString() to "正在进行",
                        w.submissions.count { it.status == "PENDING_REVIEW" }.toString() to "等待核验",
                    )
                )
            }
            item { SectionTitle("球场动态") }
            items(
                w.matches
                    .filter { it.status in listOf("IN_PROGRESS", "ACCEPTED", "PUBLISHED") }
                    .take(4),
                key = { it.id },
            ) {
                MatchCard(it, { vm.openMatch(it) })
            }
            if (w.matches.isEmpty()) item { EmptyState("赛程还未编排", "先在管理资料中添加参赛者、场地与裁判。") }
            if (w.audit.isNotEmpty()) {
                item { SectionTitle("最近操作") }
                item {
                    Panel {
                        w.audit
                            .sortedByDescending { it.at }
                            .take(4)
                            .forEach { a ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(timeLabel(a.at), fontSize = 11.sp, color = Soft)
                                    Column {
                                        Text(a.action, fontSize = 13.sp)
                                        Text(
                                            "${a.actor} · ${a.detail}".take(70),
                                            color = Soft,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                    }
                }
            }
        } else if (tab == 1) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "赛程编排",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        "新建",
                        newMatch,
                        enabled = w.tournament.status != "ARCHIVED",
                        icon = Icons.Outlined.Add,
                    )
                }
            }
            item { FilterRow(listOf("全部", "草稿", "未开始", "进行中", "已完成"), filter) { filter = it } }
            val shown =
                w.matches.filter {
                    when (filter) {
                        "草稿" -> it.status == "DRAFT"
                        "未开始" -> it.status in listOf("PUBLISHED", "ACCEPTED")
                        "进行中" -> it.status == "IN_PROGRESS"
                        "已完成" -> it.status == "FINISHED"
                        else -> true
                    }
                }
            shown
                .groupBy { dateLabel(it.startsAt) }
                .forEach { (date, ms) ->
                    item { SectionTitle(date) }
                    items(ms, key = { it.id }) { MatchCard(it, { vm.openMatch(it) }) }
                }
            if (shown.isEmpty()) item { EmptyState("暂无符合条件的比赛", "切换分类，或新建一场比赛。") }
        } else {
            item {
                Text("成绩中心", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("裁判提交后，由赛事组核验并确认正式结果。", color = Soft, fontSize = 12.sp)
            }
            val pending = w.submissions.filter { it.status == "PENDING_REVIEW" }
            item { SectionTitle("等待核验 · ${pending.size}") }
            items(pending, key = { it.id }) { s ->
                val m = w.matches.find { it.id == s.matchId }
                if (m != null) SubmissionCard(s, m) { onReview(s) }
            }
            if (pending.isEmpty())
                item {
                    Panel {
                        Text("当前成绩均已处理", fontSize = 15.sp)
                        Text("新的提交会自动出现在这里。", color = Soft, fontSize = 12.sp)
                    }
                }
            item { SectionTitle("正式成绩") }
            items(w.submissions.filter { it.status == "CONFIRMED" }, key = { it.id }) { s ->
                val m = w.matches.find { it.id == s.matchId }
                if (m != null) SubmissionCard(s, m) { onReview(s) }
            }
        }
    }
}

@Composable
private fun SubmissionCard(s: Submission, m: Match, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row {
            Text(m.round, color = Soft, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(
                if (s.status == "CONFIRMED") "已确认" else "待核验",
                color = if (s.status == "CONFIRMED") Soft else Rose,
                fontSize = 11.sp,
            )
        }
        Text(
            "${m.sideA}  ${Scoring.wins(s.score,0)} : ${Scoring.wins(s.score,1)}  ${m.sideB}",
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
        )
        Text(
            s.score.games.joinToString("    ") { "${it.a} – ${it.b}" },
            color = Soft,
            fontSize = 12.sp,
        )
        Row {
            Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(17.dp), tint = Lime)
            Spacer(Modifier.width(8.dp))
            Text("${if(s.winner==0)m.sideA else m.sideB} 获胜", color = Lime, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MatchDetail(
    state: UiState,
    organizer: Boolean,
    vm: CourtsideViewModel,
    onEdit: (Match) -> Unit,
    onReview: (Submission) -> Unit,
) {
    val m = state.selected ?: return
    var toss by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val s =
        state.workspace?.submissions?.find { it.id == m.currentSubmissionId }
            ?: state.feed.submissions.find { it.id == m.currentSubmissionId }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row {
                Text(
                    m.tournamentName,
                    color = Soft,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(m.status)
            }
        }
        item {
            Panel {
                Eyebrow(m.round, Lime)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.sideA, color = Blue, fontSize = 27.sp, fontWeight = FontWeight.Black)
                        Text(m.unitA, color = Soft, fontSize = 11.sp)
                    }
                    Text("VS", color = Soft, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(m.sideB, color = Rose, fontSize = 27.sp, fontWeight = FontWeight.Black)
                        Text(m.unitB, color = Soft, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Panel {
                MetaRow(Icons.Outlined.CalendarMonth, "比赛日期", dateLabel(m.startsAt))
                MetaRow(
                    Icons.Outlined.Schedule,
                    "预计时段",
                    "${timeLabel(m.startsAt)} — ${timeLabel(m.endsAt)}",
                )
                MetaRow(Icons.Outlined.Place, "执裁场地", m.court)
                MetaRow(Icons.Outlined.Badge, "执裁裁判", m.refereeName)
                HorizontalDivider(color = Edge)
                Text(
                    "${m.rules.gamesToWin*2-1} 局 ${m.rules.gamesToWin} 胜 · ${m.rules.target} 分制 · 领先 ${m.rules.lead} 分 · ${m.rules.cap} 分封顶",
                    color = Lime,
                    fontSize = 12.sp,
                )
                Text(
                    "安排版本 ${m.scheduleVersion} · 场次 ${m.id.take(8).uppercase()}",
                    color = Soft,
                    fontSize = 10.sp,
                )
            }
        }
        if (m.returnReason.isNotBlank())
            item {
                Panel {
                    Text("赛事组退回更正", color = Rose, fontWeight = FontWeight.Bold)
                    Text(m.returnReason, fontSize = 13.sp)
                    Text("请核对原比分，修改后重新让双方确认并提交。", color = Soft, fontSize = 11.sp)
                }
            }
        if (m.progress != null)
            item {
                Panel {
                    Text(
                        if (m.status == "IN_PROGRESS") "最近同步的比分" else "比赛结果",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${Scoring.wins(m.progress,0)} : ${Scoring.wins(m.progress,1)}",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = Lime,
                    )
                    Text(
                        m.progress.games
                            .joinToString("   ") { "${it.a} – ${it.b}" }
                            .ifBlank {
                                "本局 ${Scoring.points(m.progress.rallies,0)} – ${Scoring.points(m.progress.rallies,1)}"
                            },
                        color = Soft,
                    )
                    Text("最后更新 ${timeLabel(m.updatedAt)}", fontSize = 10.sp, color = Soft)
                }
            }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (organizer) {
                    if (m.status == "DRAFT")
                        PrimaryButton(
                            "发布至裁判端",
                            { vm.matchAction(m, "publish") },
                            Modifier.fillMaxWidth(),
                            !state.busy,
                            Icons.Outlined.Send,
                        )
                    if (m.status == "DRAFT")
                        TextButton(
                            onClick = { delete = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("删除草稿", color = Rose)
                        }
                    if (m.status in listOf("DRAFT", "PUBLISHED", "ACCEPTED")) {
                        OutlinedButton(
                            onClick = { onEdit(m) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("调整比赛安排")
                        }
                        TextButton(
                            onClick = { cancel = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("取消本场比赛", color = Rose)
                        }
                    }
                    if (s != null)
                        PrimaryButton(
                            if (s.status == "PENDING_REVIEW") "核验本场成绩" else "查看成绩记录",
                            { onReview(s) },
                            Modifier.fillMaxWidth(),
                        )
                } else
                    when (m.status) {
                        "PUBLISHED" ->
                            PrimaryButton(
                                "接受执裁任务",
                                { vm.matchAction(m, "accept") },
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
                                if (m.correctionRound > 0) "进入成绩更正" else "继续现场计分",
                                { vm.resume() },
                                Modifier.fillMaxWidth(),
                                !state.busy,
                            )
                        "PENDING_REVIEW",
                        "FINISHED" ->
                            PrimaryButton(
                                "查看逐分回顾",
                                { vm.navigate("result") },
                                Modifier.fillMaxWidth(),
                            )
                    }
                Text(
                    if (organizer) "已开始比赛的对阵、规则与裁判安排会被锁定。" else "计分先保存在本机。已开始后即使断网，也能继续完成比赛。",
                    color = Soft,
                    fontSize = 11.sp,
                )
            }
        }
    }
    if (toss)
        TossDialog(m, { toss = false }) { side ->
            toss = false
            vm.start(m, side)
        }
    if (cancel)
        ReasonDialog("取消本场比赛", "请填写取消原因，裁判端会同步更新。", { cancel = false }) {
            vm.matchAction(m, "cancel", it)
            cancel = false
        }
    if (delete)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("删除比赛草稿") },
            text = { Text("这场尚未发布的对阵将被移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        delete = false
                        vm.deleteDraft(m)
                    }
                ) {
                    Text("删除", color = Rose)
                }
            },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("保留") } },
        )
}
