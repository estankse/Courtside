package cn.courtside.referee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.courtside.client.CourtsideApp
import cn.courtside.client.RefereeLaunchHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RefereeLaunchHost { CourtsideApp(organizer = false) } }
    }
}
