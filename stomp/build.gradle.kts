plugins {
    id("org.jetbrains.kotlin.jvm")
    id ("org.jetbrains.kotlin.plugin.serialization")
}


dependencies {

    implementation("com.squareup.okio:okio:3.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation ("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("app.cash.turbine:turbine:0.12.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}