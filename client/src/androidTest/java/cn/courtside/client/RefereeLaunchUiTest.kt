package cn.courtside.client

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefereeLaunchUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun photoIntroFinishesAndDoesNotReplayAfterStateRestoration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = checkNotNull(context.getDrawable(R.mipmap.ic_referee))
        val preview = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 512, 512)
        icon.draw(Canvas(preview))
        val file = File(context.getExternalFilesDir(null), "referee-branding/launcher-icon.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            RefereeLaunchHost { CourtsideTheme { Text("裁判内容已就绪") } }
        }
        compose.onNodeWithText("轻触进入").assertIsDisplayed()
        // The visible photo must not flash past while the application loads underneath.
        SystemClock.sleep(1500)
        compose.onNodeWithText("轻触进入").assertIsDisplayed()
        compose.waitUntil(8000) {
            compose.onAllNodesWithText("轻触进入").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("裁判内容已就绪").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("轻触进入").assertDoesNotExist()
        compose.onNodeWithText("裁判内容已就绪").assertIsDisplayed()
    }

    @Test
    fun photoIntroCanBeSkippedWithoutWaitingForTheTimer() {
        compose.setContent {
            RefereeLaunchHost { CourtsideTheme { Text("立即进入裁判内容") } }
        }
        compose.onNodeWithText("轻触进入").assertIsDisplayed().performClick()
        compose.waitUntil(2000) {
            compose.onAllNodesWithText("轻触进入").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("立即进入裁判内容").assertIsDisplayed()
    }
}
