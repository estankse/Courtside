package cn.courtside.client

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.courtside.shared.Models.*
import cn.courtside.shared.Scoring
import com.google.gson.Gson
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Isolated UI and HTTP failure tests; never writes to the user's Spring database. */
@RunWith(AndroidJUnit4::class)
class OrganizerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val stores = mutableListOf<ViewModelStore>()
    private val servers = mutableListOf<OrganizerHttpFixture>()
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @After fun cleanUp() {
        stores.forEach { compose.runOnIdle { it.clear() } }
        servers.forEach { it.close() }
    }

    private fun model(server: OrganizerHttpFixture): CourtsideViewModel {
        // The instrumentation app has its own data directory, separate from both APKs.
        application.getSharedPreferences("courtside", 0).edit().clear().commit()
        val store = ViewModelStore().also { stores += it }
        return compose.runOnIdle {
            ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(application))[CourtsideViewModel::class.java].also { vm ->
                vm.repo.setUrl(server.url)
                vm.repo.updateLogin(Login().apply {
                    token = "isolated-test-token"
                    user = User().apply { id = "test-organizer"; role = "ORGANIZER" }
                })
            }
        }
    }

    private fun capture(name: String) {
        val file = File(application.getExternalFilesDir(null), "organizer-fixes/$name.png")
        file.parentFile!!.mkdirs()
        compose.waitForIdle()
        // IME animations run on Android's clock, independently of Compose's test clock.
        android.os.SystemClock.sleep(400)
        compose.waitForIdle()
        // Compose semantics can be ready before SurfaceFlinger presents the first frame.
        val frames = CountDownLatch(1)
        compose.runOnIdle {
            val decor = compose.activity.window.decorView
            decor.postOnAnimation { decor.postOnAnimation { frames.countDown() } }
        }
        assertTrue("Screen did not draw in time", frames.await(3, TimeUnit.SECONDS))
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun scoringMatch(): Match = Match().apply {
        id = "score-test"; status = "IN_PROGRESS"; sideA = "蓝方选手"; sideB = "红方选手"
        refereeName = "测试裁判"; court = "一号场地"
        startsAt = "2026-11-16T06:00:00Z"; endsAt = "2026-11-16T06:45:00Z"
        progress = ScoreSession().apply {
            repeat(21) { rallies.add(0) }
            Scoring.confirmGame(this, rules)
            repeat(5) { rallies.add(0) }; repeat(3) { rallies.add(1) }
        }
    }

    @Test fun scoreDetailsShowSealedGameAndCurrentGameTogether() {
        val match = scoringMatch()
        compose.setContent {
            CourtsideTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = Ink) {
                Column { OrganizerScoreDetails(match) }
            } }
        }
        compose.onNodeWithText("第 1 局  21 – 0").assertIsDisplayed()
        compose.onNodeWithText("第 2 局（未封存） 5 – 3").assertIsDisplayed()
        capture("ongoing-score")
    }

    @Test fun finishedCardUsesMatchResultInsteadOfClearedRallies() {
        val match = scoringMatch().apply {
            status = "FINISHED"
            progress.rallies.clear()
            repeat(21) { progress.rallies.add(0) }
            Scoring.confirmGame(progress, rules)
        }
        assertEquals("局胜 2 : 0", organizerScoreLabel(match))
        compose.setContent {
            CourtsideTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = Ink) {
                Column { OrganizerMatchCard(match) {} }
            } }
        }
        compose.onNodeWithText("局胜 2 : 0").assertIsDisplayed()
        compose.onNodeWithText("蓝方选手 获胜").assertIsDisplayed()
        capture("finished-result")
    }

    @Test fun retirementReviewIncludesUnsealedPoints() {
        val match = scoringMatch()
        val submission = Submission().apply {
            id = "retirement-test"; score = match.progress; winner = 1
            score.retirementWinner = 1; score.retirementReason = "伤病弃权"
        }
        compose.setContent { CourtsideTheme { ReviewDialog(submission, match, {}) { _, _ -> } } }
        compose.onNodeWithText("第 2 局（未封存） 5 – 3").performScrollTo().assertIsDisplayed()
        capture("retirement-review")
    }

    @Test fun failedSaveRetainsInputAndSuccessfulRetryClosesForm() {
        val server = OrganizerHttpFixture(failedWrites = 1).also { servers += it }
        val vm = model(server)
        compose.setContent {
            val state by vm.ui.collectAsState()
            var open by rememberSaveable { mutableStateOf(true) }
            CourtsideTheme {
                if (open) TournamentForm(null, true, {}, state.busy, state.organizerError) { body ->
                    vm.createTournament(body) { open = false }
                } else TextForTest("保存完成")
            }
        }
        compose.onNodeWithText("赛事名称").performTextInput("失败后保留的赛事")
        compose.onNodeWithText("举办地点").performTextInput("测试体育馆")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { vm.ui.value.organizerError != null && !vm.ui.value.busy }
        compose.onNodeWithText("失败后保留的赛事").assertExists()
        compose.onNodeWithText("测试体育馆").assertExists()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("保存完成").assertExists()
        compose.waitUntil(10000) { !vm.ui.value.busy }
        assertEquals(2, server.writes.get())
    }

    @Test fun pendingSaveDisablesSubmitAndDoesNotDismiss() {
        val server = OrganizerHttpFixture(holdWrite = true).also { servers += it }
        val vm = model(server)
        compose.setContent {
            val state by vm.ui.collectAsState()
            CourtsideTheme { TournamentForm(null, true, {}, state.busy, state.organizerError) { vm.createTournament(it) } }
        }
        compose.onNodeWithText("赛事名称").performTextInput("等待保存")
        compose.onNodeWithText("举办地点").performTextInput("体育馆")
        compose.onNodeWithText("保存").performClick()
        try {
            compose.waitUntil(10000) { server.writes.get() == 1 }
        } catch (e: Throwable) {
            capture("pending-save-failure")
            throw AssertionError("writes=${server.writes.get()}, busy=${vm.ui.value.busy}, error=${vm.ui.value.organizerError}, issue=${vm.ui.value.issue}", e)
        }
        compose.onNodeWithText("正在提交…").assertIsNotEnabled()
        compose.onNodeWithContentDescription("关闭").assertIsNotEnabled()
        compose.onNodeWithText("等待保存").assertExists()
        server.releaseWrite.countDown()
        compose.waitUntil(10000) { !vm.ui.value.busy }
        assertEquals(1, server.writes.get())
    }

    @Test fun savedWriteAndFailedRefreshAreReportedSeparately() {
        val server = OrganizerHttpFixture(failReads = true).also { servers += it }
        val vm = model(server)
        var closed = false
        compose.runOnIdle {
            vm.createTournament(mapOf("name" to "已保存", "location" to "馆", "date" to "2026-11-16")) { closed = true }
        }
        compose.waitUntil(10000) { closed && !vm.ui.value.busy }
        assertEquals(1, server.writes.get())
        assertTrue(vm.ui.value.message!!.contains("列表暂未刷新"))
        assertNull(vm.ui.value.organizerError)
        assertNotNull(vm.ui.value.organizerLoadError)
    }

    @Test fun formStateSurvivesRecreationAndRulesHaveFieldErrors() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { CourtsideTheme { TournamentForm(null, true, {}) {} } }
        compose.onNodeWithText("赛事名称").performTextInput("需要恢复的赛事")
        compose.onNodeWithText("举办地点").performTextInput("需要恢复的场馆")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("需要恢复的赛事").assertExists()
        compose.onNodeWithText("需要恢复的场馆").assertExists()
        compose.onNodeWithText("封顶分").performScrollTo().performTextReplacement("20")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("封顶分不能小于目标分").performScrollTo()
        capture("restored-form-validation")
        compose.onNodeWithText("封顶分不能小于目标分").assertIsDisplayed()
    }

    @Test fun archivedSettingsRemainReadOnlyWithLargeText() {
        val tournament = Tournament().apply {
            name = "归档赛事与较长的赛事名称测试"; location = "测试场馆"; status = "ARCHIVED"
        }
        var writes = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                CourtsideTheme { TournamentForm(tournament, false, {}) { writes++ } }
            }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("赛事已归档，资料仅供查询。").assertExists()
        compose.onNodeWithText("关闭").assertIsDisplayed()
        assertEquals(0, writes)
        capture("archived-large-text")
    }

    @Test fun matchFormRestoresSelectionsAndDistinguishesSameNames() {
        val work = Workspace().apply {
            tournament = Tournament().apply { id = "restore-match"; date = "2026-11-16" }
            listOf("11111111-a", "22222222-b", "33333333-c").forEach { key ->
                entrants.add(Entrant().apply { id = key; name = "同名选手与较长姓名测试"; unit = "同一学院" })
            }
            courts.add(Court().apply { id = "court"; name = "一号场地" })
            listOf("ref_one", "ref_two").forEach { account ->
                referees.add(User().apply { id = account; name = "同名裁判"; username = account })
            }
        }
        var saved: Map<String, Any>? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { CourtsideTheme { MatchForm(work, null, {}) { saved = it } } }
        compose.onNodeWithText("轮次或场次名称").performTextReplacement("恢复后的比赛")
        compose.onNodeWithText("蓝方").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText(entrantLabel(work.entrants[2])).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(entrantLabel(work.entrants[2])).performScrollTo().performClick()
        compose.onNodeWithText("执裁裁判").performScrollTo().performClick()
        compose.onNodeWithText("同名裁判 · ref_two").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("恢复后的比赛").assertExists()
        compose.onNodeWithText(entrantLabel(work.entrants[2])).assertExists()
        compose.onNodeWithText("同名裁判 · ref_two").assertExists()
        capture("restored-match-form")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals("33333333-c", saved?.get("sideAId"))
            assertEquals("22222222-b", saved?.get("sideBId"))
            assertEquals("ref_two", saved?.get("refereeId"))
            assertEquals("恢复后的比赛", saved?.get("round"))
        }
    }

    @Test fun organizerColdStartIgnoresOldLoginAndAsksForPassword() {
        val prefs = application.getSharedPreferences("courtside", 0)
        val oldLogin = Login().apply {
            token = "old-organizer-token"
            user = User().apply { id = "test-organizer"; role = "ORGANIZER" }
        }
        prefs.edit().clear().putString("login", Gson().toJson(oldLogin)).commit()
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(application))[CourtsideViewModel::class.java]
        var organizer by mutableStateOf(true)
        compose.setContent { CourtsideApp(organizer, vm) }
        compose.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("进入赛事组工作台").performScrollTo().assertIsNotEnabled()
        assertNull(vm.ui.value.login)
        assertFalse(prefs.contains("login"))
        compose.runOnIdle { organizer = false }
        compose.onNodeWithText("进入裁判工作台").performScrollTo().assertIsEnabled()
    }

    @Test fun loadingFailureIsNotPresentedAsAnEmptyTournamentList() {
        val server = OrganizerHttpFixture(failReads = true).also { servers += it }
        val vm = model(server)
        compose.runOnIdle { vm.signIn(server.url, "organizer", "test-password", true) }
        compose.waitUntil(10000) { vm.ui.value.organizerLoadError != null }
        compose.setContent { CourtsideApp(true, vm) }
        compose.onNodeWithText("赛事资料加载失败").assertIsDisplayed()
        compose.onNodeWithText("重新加载").assertIsDisplayed()
        compose.onNodeWithText("创建你的第一项赛事").assertDoesNotExist()
    }

    @Test fun organizerOfflineBannerExplainsCachedDataAndOnlineOperations() {
        val server = OrganizerHttpFixture().also { servers += it }
        val vm = model(server)
        compose.setContent { CourtsideApp(true, vm) }
        compose.runOnIdle { vm.signIn(server.url, "organizer", "test-password", true) }
        compose.waitUntil(10000) { vm.ui.value.organizerLoaded && !vm.ui.value.busy && !vm.ui.value.organizerLoading }
        compose.onAllNodesWithText("测试赛事").onFirst().assertIsDisplayed()
        server.close()
        compose.runOnIdle { vm.refreshNow() }
        compose.waitUntil(10000) { !vm.ui.value.online }
        capture("organizer-offline")
        compose.onNodeWithText("当前离线 · 正在查看缓存，编排、发布和核验需联网").assertIsDisplayed()
        compose.onNodeWithText("当前离线 · 已开始比赛可继续计分").assertDoesNotExist()
        capture("organizer-offline")
    }

    @Test fun scheduleFiltersCombineAndHistoryRecordsAreReadOnly() {
        val work = Workspace().apply {
            tournament = Tournament().apply { id = "schedule-test"; name = "赛程测试" }
            courts = listOf(
                Court().apply { id = "court-a"; name = "测试一号场" },
                Court().apply { id = "court-b"; name = "测试二号场" },
            )
        }
        fun match(id: String, roundName: String, court: Court, day: String, state: String) =
            Match().apply {
                this.id = id
                tournamentId = work.tournament.id
                round = roundName
                courtId = court.id
                this.court = court.name
                startsAt = "2026-11-${day}T16:30:00Z"
                endsAt = "2026-11-${day}T17:00:00Z"
                status = state
                sideA = "蓝方选手"
                sideB = "红方选手"
            }
        val cancelled = match("cancelled", "目标取消场次", work.courts[0], "16", "CANCELLED").apply {
            cancelReason = "场馆临时闭馆"
        }
        val pending = match("pending", "待核验场次", work.courts[0], "16", "PENDING_REVIEW").apply {
            currentSubmissionId = "current"
        }
        work.matches = listOf(
            cancelled, pending,
            match("different-court", "其他场地场次", work.courts[1], "16", "CANCELLED"),
            match("different-day", "其他日期场次", work.courts[0], "17", "CANCELLED"),
        )
        work.submissions = listOf(
            Submission().apply {
                id = "previous"; matchId = pending.id; status = "RETURNED"
                createdAt = "2026-11-16T17:00:00Z"
                reviewedAt = "2026-11-16T17:05:00Z"
                reviewReason = "请核对第二局比分"
                winner = 0
            },
            Submission().apply {
                id = "current"; matchId = pending.id; status = "PENDING_REVIEW"
                createdAt = "2026-11-16T17:10:00Z"
                score.correctionRound = 1
                score.games.add(Game().apply { a = 21; b = 18; corrected = true; correctionNote = "按现场记录更正" })
                winner = 0
            },
        )
        val fixture = openWorkspace(work)
        compose.onNodeWithText("赛程").performClick()
        scrollToText("筛选赛程").performClick()
        scrollToText("比赛日期").performClick()
        selectMenuOption("2026-11-17")
        scrollToText("比赛场地").performClick()
        selectMenuOption("测试一号场")
        scrollToText("比赛状态").performClick()
        selectMenuOption("已取消")
        scrollToText("符合条件 1 场").assertIsDisplayed()
        scrollToText("目标取消场次").performClick()
        scrollToText("场馆临时闭馆").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回工作台").performClick()
        scrollToText("清空筛选").performClick()
        scrollToText("其他场地场次").assertIsDisplayed()
        scrollToText("待核验场次").performClick()
        scrollToText("提交与核验历史 · 2").assertIsDisplayed()
        compose.onNodeWithText("展开").performClick()
        scrollToText("已退回 · 第 1 次提交").assertIsDisplayed()
        scrollToText("请核对第二局比分").assertIsDisplayed()
        scrollToText("待核验 · 第 2 次提交 · 当前有效提交").assertIsDisplayed()
        compose.onAllNodesWithText("查看提交详情").onFirst().performClick()
        compose.onNodeWithText("提交详情").assertIsDisplayed()
        compose.onNodeWithText("确认正式成绩").assertDoesNotExist()
        compose.onNodeWithText("退回更正").assertDoesNotExist()
        compose.onNodeWithText("局分更正：按现场记录更正").performScrollTo().assertIsDisplayed()
        assertEquals(0, fixture.writes.get())
    }

    @Test fun overviewCountsAllStatusesAndAuditShowsOldUnabridgedRecords() {
        val work = Workspace().apply {
            tournament = Tournament().apply { id = "audit-test"; name = "审计测试" }
            matches = organizerMatchStatuses.map { (state, _) ->
                Match().apply { id = state; status = state; round = "测试场次 $state" }
            }
        }
        val longDetail = "这是用于检查全文展示的长操作记录。".repeat(8) + "最后的细节"
        work.audit = (1..6).map { index ->
            Audit().apply {
                id = "audit-$index"
                matchId = if (index == 1) "deleted-draft" else ""
                actor = "测试操作者"
                action = if (index == 1) "删除草稿" else "更新赛事"
                detail = if (index == 1) longDetail else "更新记录 $index"
                at = "2026-11-16T12:00:0${index}Z"
            }
        }
        openWorkspace(work)
        scrollToText("比赛总数")
        organizerMatchStatuses.forEach { (_, label) ->
            compose.onNode(
                hasText(label) and hasAnySibling(hasText("1")) and
                    hasAnyAncestor(hasTestTag("organizer-status-counts"))
            ).assertIsDisplayed()
        }
        scrollToText("全部操作记录").performClick()
        scrollToText("按场次筛选").performClick()
        selectMenuOption("场次 deleted-")
        scrollToText("按操作筛选").performClick()
        selectMenuOption("删除草稿")
        scrollToText(longDetail).assertIsDisplayed()
        scrollToText("2026-11-16 20:00:01 · 测试操作者").assertIsDisplayed()
    }

    private fun openWorkspace(work: Workspace): OrganizerHttpFixture {
        val fixture = OrganizerHttpFixture(workspace = work).also { servers += it }
        val vm = model(fixture)
        compose.setContent { CourtsideApp(true, vm) }
        compose.runOnIdle { vm.signIn(fixture.url, "organizer", "test-password", true) }
        compose.waitUntil(10000) { vm.ui.value.workspace?.tournament?.id == work.tournament.id && !vm.ui.value.busy }
        return fixture
    }

    private fun scrollToText(text: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text)
    }

    private fun selectMenuOption(text: String) {
        compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
    }

    @Test fun accountValidationAndArchiveReasonsCoverBoundaryCases() {
        assertNull(refereeAccountError("ref_1.-", "1234567890"))
        assertNotNull(refereeAccountError("ab", "1234567890"))
        assertNotNull(refereeAccountError("a".repeat(41), "1234567890"))
        assertNotNull(refereeAccountError("ref", "a".repeat(73)))
        assertNotNull(refereeAccountError("ref", " 1234567890"))
        assertTrue(organizerRuleErrors("abc", "30", "2", "2").containsKey("target"))
        assertTrue(organizerRuleErrors("21", "20", "2", "2").containsKey("cap"))
        val work = Workspace()
        assertTrue(archiveBlockReason(work)!!.contains("空赛事"))
        work.matches.add(Match())
        assertTrue(archiveBlockReason(work)!!.contains("1 场"))
        work.matches[0].status = "FINISHED"
        assertNull(archiveBlockReason(work))
    }
}

@Composable private fun TextForTest(text: String) { androidx.compose.material3.Text(text) }

private class OrganizerHttpFixture(
    private val failedWrites: Int = 0,
    private val failReads: Boolean = false,
    private val holdWrite: Boolean = false,
    private val workspace: Workspace = Workspace().apply {
        tournament = Tournament().apply { id = "isolated-tournament"; name = "测试赛事"; date = "2026-11-16" }
    },
) : AutoCloseable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val url = "http://127.0.0.1:${server.localPort}/api"
    val writes = AtomicInteger()
    val releaseWrite = CountDownLatch(if (holdWrite) 1 else 0)
    private val gson = Gson()
    private val tournament = workspace.tournament
    init {
        thread(isDaemon = true, name = "organizer-http-fixture") {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream()
                    val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) {
                        val byte = input.read(); if (byte < 0) break
                        header.append(byte.toChar())
                    }
                    val lines = header.toString().split("\r\n")
                    val request = lines.first().split(" ")
                    val length = lines.find { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                    repeat(length) { input.read() }
                    val loginRequest = request[1].endsWith("/auth/login")
                    val write = request[0] == "POST" && !loginRequest
                    val number = if (write) writes.incrementAndGet() else 0
                    if (write && holdWrite) releaseWrite.await(10, TimeUnit.SECONDS)
                    val failure = !loginRequest && ((write && number <= failedWrites) || (!write && failReads))
                    val body = if (failure) """{"code":"VALIDATION","message":"测试保存失败，请重试"}"""
                    else if (loginRequest) gson.toJson(Login().apply {
                        token = "isolated-test-token"
                        user = User().apply { id = "test-organizer"; name = "测试赛事组"; role = "ORGANIZER" }
                    })
                    else if (write) gson.toJson(tournament)
                    else if (request[1].endsWith("/workspace")) gson.toJson(workspace)
                    else if (request[1].endsWith("/tournaments")) gson.toJson(listOf(tournament))
                    else "[]"
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val status = if (failure) "400 Bad Request" else "200 OK"
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                }
            } catch (_: SocketException) { /* closed by test cleanup */ }
        }
    }
    override fun close() { releaseWrite.countDown(); server.close() }
}
