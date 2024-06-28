package ru.krasov.stomp

import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.net.Socket

internal fun readResourceFile(fileName: String): String {
    return File(ClassLoader.getSystemResource(fileName).file).readText(Charsets.UTF_8)
}

fun isMockWebServerRunning(mockWebServer: MockWebServer): Boolean {
    return try {
        Socket("localhost", mockWebServer.port).close()
        true
    } catch (e: Exception) {
        false
    }
}