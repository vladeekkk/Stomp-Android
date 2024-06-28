package ru.krasov.stomp.okhttp

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import ru.krasov.stomp.core.model.StompHeader

interface WebSocketFactory {

    fun createWebsocket(
        okHttpClient: OkHttpClient,
        listener: WebSocketListener,
        request: Request
    ): WebSocket
}

class WebSocketFactoryOkHttp : WebSocketFactory {

    override fun createWebsocket(
        okHttpClient: OkHttpClient,
        listener: WebSocketListener,
        request: Request
    ): WebSocket {
        return okHttpClient.newWebSocket(request, listener)
    }
}
