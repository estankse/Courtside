package cn.courtside.client

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.courtside.shared.Models.*
import com.google.gson.Gson
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

/** Runs against the real local Spring service on the emulator host. */
@RunWith(AndroidJUnit4::class)
class WorkflowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    val gson = Gson()
    private val serviceUrl =
        InstrumentationRegistry.getArguments().getString("baseUrl") ?: "http://10.0.2.2:8080/api"

    private fun <T> api(
        method: String,
        path: String,
        body: Any?,
        token: String?,
        type: Class<T>,
    ): T {
        val c = URL("$serviceUrl$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10000
            c.readTimeout = 10000
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(gson.toJson(body).toByteArray()) }
            }
            val text =
                (if (c.responseCode == 200) c.inputStream else c.errorStream)
                    .bufferedReader()
                    .readText()
            Assert.assertEquals(text, 200, c.responseCode)
            return gson.fromJson(text, type)
        } finally {
            c.disconnect()
        }
    }

    private fun login(name: String) =
        api(
            "POST",
            "/auth/login",
            mapOf("username" to name, "password" to "Courtside2026!"),
            null,
            Login::class.java,
        )

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val file =
            File(
                InstrumentationRegistry.getInstrumentation()
                    .targetContext
                    .getExternalFilesDir(null),
                "screenshots/$name.png",
            )
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun realServiceRefereeAndOrganizerWorkflow() {
        val org = login("organizer")
        val ref = login("referee")
        val rules =
            Rules().apply {
                target = 3
                cap = 5
                gamesToWin = 1
            }
        val eventName = "界面验收赛 ${UUID.randomUUID().toString().take(4)}"
        val roundName = "联调验收 · ${UUID.randomUUID().toString().take(4)}"
        val t =
            api(
                "POST",
                "/tournaments",
                mapOf(
                    "name" to eventName,
                    "location" to "中心球场",
                    "date" to "2026-10-20",
                    "rules" to rules,
                ),
                org.token,
                Tournament::class.java,
            )
        api(
            "POST",
            "/tournaments/${t.id}/members",
            mapOf("userId" to ref.user.id),
            org.token,
            User::class.java,
        )
        val a =
            api(
                "POST",
                "/tournaments/${t.id}/entrants",
                mapOf("name" to "验收蓝方", "unit" to "计算机学院"),
                org.token,
                Entrant::class.java,
            )
        val b =
            api(
                "POST",
                "/tournaments/${t.id}/entrants",
                mapOf("name" to "验收红方", "unit" to "信息学院"),
                org.token,
                Entrant::class.java,
            )
        val court =
            api(
                "POST",
                "/tournaments/${t.id}/courts",
                mapOf("name" to "01 号场地"),
                org.token,
                Court::class.java,
            )
        val m =
            api(
                "POST",
                "/tournaments/${t.id}/matches",
                mapOf(
                    "sideAId" to a.id,
                    "sideBId" to b.id,
                    "courtId" to court.id,
                    "refereeId" to ref.user.id,
                    "startsAt" to Instant.now().plusSeconds(3600).toString(),
                    "endsAt" to Instant.now().plusSeconds(5400).toString(),
                    "round" to roundName,
                ),
                org.token,
                Match::class.java,
            )
        api(
            "POST",
            "/matches/${m.id}/publish",
            emptyMap<String, String>(),
            org.token,
            Match::class.java,
        )
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.getSharedPreferences("courtside", 0).edit().clear().commit()
        val vm = CourtsideViewModel(application)
        vm.repo.setUrl(serviceUrl)
        val organizer = mutableStateOf(false)
        compose.setContent { CourtsideApp(organizer.value, vm) }
        compose.onNodeWithText("进入裁判工作台").assertExists()
        screenshot("01-login")
        compose.onNodeWithText("进入裁判工作台").performScrollTo().performClick()
        compose.waitUntil(20000) { vm.ui.value.feed.matches.any { it.id == m.id } }
        screenshot("02-referee-home")
        compose.onNodeWithText("我的赛程").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(roundName))
        compose.onNodeWithText(roundName).performClick()
        compose.onNodeWithText("接受执裁任务").performScrollTo().performClick()
        compose.waitUntil(15000) { vm.ui.value.selected?.status == "ACCEPTED" && !vm.ui.value.busy }
        compose.onNodeWithText("赛前准备与抽签").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("确认并开始比赛").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("确认并开始比赛").performClick()
        compose.waitUntil(15000) { vm.ui.value.route == "score" }
        screenshot("03-score-dark")
        compose.onNodeWithContentDescription("切换明暗计分模式").performClick()
        screenshot("04-score-light")
        compose.onNodeWithContentDescription("切换明暗计分模式").performClick()
        compose.onNodeWithContentDescription("验收蓝方 加一分，当前 0 分").performClick()
        compose.waitUntil(5000) { vm.ui.value.score?.rallies?.size == 1 }
        compose.onNodeWithContentDescription("验收蓝方 加一分，当前 1 分").performClick()
        compose.waitUntil(5000) { (vm.ui.value.score?.breakUntil ?: 0) > 0 }
        compose.onNodeWithText("结束间歇，继续比赛").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { vm.ui.value.score?.breakUntil == 0L }
        runBlocking { Assert.assertEquals(2, vm.repo.session(m.id)!!.rallies.size) }
        compose.onNodeWithContentDescription("验收蓝方 加一分，当前 2 分").performClick()
        compose.waitUntil(5000) { vm.ui.value.score?.rallies?.size == 3 }
        compose.onNodeWithText("确认本局结果").performClick()
        compose.waitUntil(10000) { vm.ui.value.route == "result" }
        compose.onNodeWithText("验收蓝方").performScrollTo().performClick()
        compose.onNodeWithText("验收红方").performScrollTo().performClick()
        compose.waitUntil(5000) {
            vm.ui.value.score?.aConfirmed == true && vm.ui.value.score?.bConfirmed == true
        }
        compose.waitForIdle()
        compose.onNodeWithText("确认结果并提交云端").performScrollTo().assertIsEnabled().performClick()
        try {
            compose.waitUntil(20000) { vm.ui.value.selected?.status == "PENDING_REVIEW" }
        } catch (e: Throwable) {
            screenshot("05-sync-failure")
            val diagnostic = File(application.getExternalFilesDir(null), "failure.txt")
            diagnostic.writeText(
                "route=${vm.ui.value.route}; issue=${vm.ui.value.issue}; message=${vm.ui.value.message}; pending=${vm.ui.value.pending}; submitted=${vm.ui.value.score?.submitted}"
            )
            throw e
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        screenshot("05-result-recap")
        compose.runOnIdle {
            vm.logout()
            organizer.value = true
        }
        compose.onNodeWithText("进入赛事组工作台").performScrollTo().performClick()
        compose.waitUntil(20000) { vm.ui.value.workspace != null }
        compose.onNodeWithText("赛事总览").performClick()
        if (vm.ui.value.workspace?.tournament?.id != t.id) {
            compose.onNodeWithContentDescription("选择当前赛事").performClick()
            compose.onNodeWithText(eventName).performScrollTo().performClick()
        }
        compose.waitUntil(15000) { vm.ui.value.workspace?.tournament?.id == t.id }
        screenshot("06-organizer-home")
        compose.onNodeWithText("成绩").performClick()
        compose.onNodeWithText("验收蓝方  1 : 0  验收红方").performScrollTo().performClick()
        compose.onNodeWithText("确认正式成绩").performClick()
        compose.waitUntil(20000) {
            vm.ui.value.workspace?.matches?.find { it.id == m.id }?.status == "FINISHED"
        }
        screenshot("07-confirmed-results")
        Assert.assertEquals(
            "FINISHED",
            api("GET", "/matches/${m.id}", null, ref.token, Match::class.java).status,
        )
    }
}
