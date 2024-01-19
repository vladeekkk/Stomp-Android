package ru.krasov.stomp.okhttp.client

import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import ru.krasov.stomp.okhttp.core.Connection
import ru.krasov.stomp.okhttp.core.IdGenerator
import ru.krasov.stomp.okhttp.core.MessageHandler
import ru.krasov.stomp.okhttp.core.StompListener
import ru.krasov.stomp.okhttp.core.StompSender
import ru.krasov.stomp.okhttp.core.StompSubscriber
import ru.krasov.stomp.okhttp.core.WebSocketFactory
import ru.krasov.stomp.okhttp.model.StompCommand
import ru.krasov.stomp.okhttp.model.StompHeader
import ru.krasov.stomp.okhttp.model.StompMessage
import ru.krasov.stomp.okhttp.core.Channel
import ru.krasov.stomp.okhttp.support.StompHeaderAccessor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class OkHttpStompClient(
    private val configuration: OkHttpStompClientConfiguration,
    private val clientOpenRequest: ClientOpenRequest,
    private val webSocketFactory: WebSocketFactory,
    private val idGenerator: IdGenerator
) : Channel, StompSender, StompSubscriber {

    companion object {

        /** STOMP recommended error of margin for receiving heartbeats.  */
        private const val HEARTBEAT_MULTIPLIER = 3

        private const val ACCEPT_VERSION = "1.1,1.2"
    }

    private val topicIds = ConcurrentHashMap<String, String>()
    private val subscriptions = ConcurrentHashMap<String, StompListener>()

    private var messageHandler: MessageHandler? = null
    private var connection: Connection? = null

    override fun open() {
        webSocketFactory.createWebSocket(
            clientOpenRequest.okHttpRequest,
            InnerWebSocketListener(clientOpenRequest)
        )
    }

    override fun subscribe(destination: String, headers: StompHeader?, listener: StompListener) {
        check(!topicIds.containsKey(destination)) {
            "Already has subscription to destination=$destination"
        }
        check(!subscriptions.containsKey(destination)) {
            "Already has subscription to destination=$destination"
        }
        val generateId = idGenerator.generateId()
        val stompHeaders = StompHeaderAccessor.of(headers.orEmpty())
            .apply {
                this.subscriptionId = generateId
                this.destination = destination
            }
            .createHeader()

        val stompMessage = StompMessage.Builder()
            .withHeaders(stompHeaders)
            .create(StompCommand.SUBSCRIBE)

        connection?.sendMessage(stompMessage)

        topicIds[destination] = generateId
        subscriptions[destination] = listener
    }

    override fun convertAndSend(
        payload: ByteArray,
        destination: String,
        headers: StompHeader?
    ): Boolean {
        val stompHeaders = StompHeaderAccessor.of(headers.orEmpty())
            .apply { this.destination = destination }
            .createHeader()

        val stompMessage = StompMessage.Builder()
            .withPayload(payload)
            .withHeaders(stompHeaders)
            .create(StompCommand.SEND)

        return connection?.sendMessage(stompMessage) ?: false
    }

    override fun unsubscribe(destination: String) {
        val subscriptionId = topicIds.remove(destination) ?: return

        val stompHeaders = StompHeaderAccessor.of()
            .apply { this.subscriptionId = subscriptionId }
            .createHeader()

        val unsubscribeStompMessage = StompMessage.Builder()
            .withHeaders(stompHeaders)
            .create(StompCommand.UNSUBSCRIBE)

        connection?.sendMessage(unsubscribeStompMessage)
        subscriptions.remove(destination)
    }

    override fun close() {
        disconnect()
        connection?.close()

        connection = null
        messageHandler = null
    }

    override fun forceClose() {
        disconnect()
        connection?.forceClose()

        connection = null
        messageHandler = null
    }

    private fun disconnect() {
        subscriptions.keys.forEach(::unsubscribe)

        topicIds.clear()
        subscriptions.clear()

        sendDisconnectMessage()
    }

    private fun sendDisconnectMessage() {
        val disconnectStompMessage = StompMessage.Builder()
            .create(StompCommand.DISCONNECT)

        connection?.sendMessage(disconnectStompMessage)
    }

    data class ClientOpenRequest(
        val okHttpRequest: Request,
        val login: String? = null,
        val passcode: String? = null
    )

    private inner class InnerWebSocketListener(
        private val openRequest: ClientOpenRequest
    ) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            val webSocketConnection = WebSocketConnection(webSocket)
            this@OkHttpStompClient.connection = webSocketConnection
            this@OkHttpStompClient.messageHandler = webSocketConnection

            val host = configuration.host
            val login = openRequest.login
            val passcode = openRequest.passcode

            sendConnectMessage(host, login, passcode)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            messageHandler?.handle(bytes.toByteArray())?.let(::handleIncome)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            messageHandler?.handle(text.toByteArray(Charsets.UTF_8))?.let(::handleIncome)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (WebSocketCode.isUnexpectedClose(code)) {
                this@OkHttpStompClient.connection = null
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this@OkHttpStompClient.connection = null
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
            this@OkHttpStompClient.connection = null
        }
    }

    private fun sendConnectMessage(host: String, login: String? = null, passcode: String? = null) {
        val stompHeaderAccessor = StompHeaderAccessor.of()
            .apply {
                this.host = host
                this.acceptVersion = ACCEPT_VERSION
                this.login = login
                this.passcode = passcode
            }

        if (configuration.shouldSendHeartBeat) {
            stompHeaderAccessor.heartBeat = configuration.headerBeatPair
        }

        val connectStompMessage = StompMessage.Builder()
            .withHeaders(stompHeaderAccessor.createHeader())
            .create(StompCommand.CONNECT)

        connection?.sendMessage(connectStompMessage)
    }

    private fun handleIncome(stompMessage: StompMessage) = when (stompMessage.command) {
        StompCommand.CONNECTED -> setupHeartBeat(stompMessage)
        StompCommand.MESSAGE -> Unit
        StompCommand.ERROR -> Unit
        else -> Unit // not a server message
    }

    private fun setupHeartBeat(stompMessage: StompMessage) {
        val (serverSendInterval, serverReceiveInterval) = stompMessage.headers.heartBeat

        val clientSendInterval = configuration.heartbeatSendInterval
        val clientReceiveInterval = configuration.heartbeatReceiveInterval

        if (clientSendInterval > 0 && serverReceiveInterval > 0) {
            val interval = max(clientSendInterval, serverReceiveInterval)
            connection?.onWriteInactivity(interval, ::sendHeartBeat)
        }

        if (clientReceiveInterval > 0 && serverSendInterval > 0) {
            val interval = max(clientReceiveInterval, serverSendInterval) * HEARTBEAT_MULTIPLIER
            connection?.onReceiveInactivity(interval) {
                sendErrorMessage("No messages received in $interval ms.")
                connection?.close()
            }
        }
    }

    private fun sendHeartBeat() {
        val heartbeatStompMessage = StompMessage.Builder()
            .create(StompCommand.HEARTBEAT)

        connection?.sendMessage(heartbeatStompMessage)
    }

    private fun sendErrorMessage(error: String) {
        val headers = StompHeaderAccessor.of()
            .apply { message(error) }
            .createHeader()

        val stompMessage = StompMessage.Builder()
            .withHeaders(headers)
            .create(StompCommand.ERROR)

        connection?.sendMessage(stompMessage)
    }
}
