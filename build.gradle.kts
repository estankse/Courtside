plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
    id("org.springframework.boot") version "3.4.6" apply false
}

allprojects {
    group = "cn.courtside"
    version = "1.0.0"
    layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("../tmp/builds/$name"))
}
