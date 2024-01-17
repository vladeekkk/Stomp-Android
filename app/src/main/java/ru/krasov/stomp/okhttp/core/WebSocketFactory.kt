package ru.krasov.stomp.okhttp.core

import okhttp3.Request
import okhttp3.WebSocketListener

interface WebSocketFactory {

    fun createWebSocket(request: Request, listener: WebSocketListener)
}
