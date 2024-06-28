package ru.krasov.stomp.okhttp

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import ru.krasov.stomp.OkHttpStompClientConfiguration
import ru.krasov.stomp.core.Engine
import ru.krasov.stomp.core.IdGenerator
import ru.krasov.stomp.core.MessageHandler
import ru.krasov.stomp.core.UuidGenerator
import ru.krasov.stomp.core.WebSocketConnection
import ru.krasov.stomp.core.model.StompCommand
import ru.krasov.stomp.core.model.StompMessage
import ru.krasov.stomp.core.support.StompHeaderAccessor
import ru.krasov.stomp.state.StompClientState
import ru.krasov.stomp.state.StompClientState.NotConnected
import ru.krasov.stomp.state.StompEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

private const val HEARTBEAT_MULTIPLIER = 3

class OkHttpEngine(
    private val okHttpClient: OkHttpClient,
    private val webSocketFactory: WebSocketFactory = WebSocketFactoryOkHttp(),
    private val clientConfiguration: OkHttpStompClientConfiguration,
    private val idGenerator: IdGenerator = UuidGenerator()
) : Engine {

    private var webSocket: WebSocket? = null
    private var connection: WebSocketConnection? = null
    private var messageHandler: MessageHandler? = null

    private val topicIds = ConcurrentHashMap<String, String>() // destination -> topicId

    private val state: MutableStateFlow<StompClientState> = MutableStateFlow(NotConnected)

    override val observe: MutableSharedFlow<StompEvent> = MutableSharedFlow(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun connect() {
        if (state.value != NotConnected) {
            println()
            return
        }
        val connectHeaders = StompHeaderAccessor.of()
            .apply {
                this.host = "ws://${clientConfiguration.host}:${clientConfiguration.port}"
                this.acceptVersion = clientConfiguration.acceptVersion
                this.login = clientConfiguration.login
                this.passcode = clientConfiguration.passcode
                this.heartBeat = clientConfiguration.headerBeatPair
            }.createHeader()

        println("LOG_TAG OkHttpEngine#connect(); headers = ${connectHeaders.values}")

        val connectStompMessage = StompMessage.Builder()
            .withHeaders(connectHeaders)
            .create(StompCommand.CONNECT)

        val request = Request.Builder()
            .url(connectHeaders["host"]!!)
            .build()

        webSocket = webSocketFactory.createWebsocket(
            okHttpClient = okHttpClient,
            request = request,
            listener = InnerWebSocketListener(
                onOpenCallback = { internalSend(connectStompMessage) },
                onMessageCallback = { message -> handleIncome(message) }
            )
        )
    }


    override fun <T> convertAndSend(destination: String, data: T): Boolean {
        println("LOG_TAG OkHttpEngine#convertAndSend(dest=$destination;data=${data}}")
        println("LOG_TAG ${data!!::class}")
        return when (data) {
            is StompCommand -> {
                println("LOG_TAG data is StompCommand")
                when (data) {
                    StompCommand.SUBSCRIBE -> subscribe(destination)
                    StompCommand.UNSUBSCRIBE -> unsubscribe(destination)
                    else -> false
                }
            }
            is StompMessage -> {
                println("LOG_TAG data is StompMessage")
                internalSend(message = data)
            }

            is String -> {
                println("LOG_TAG data is String")
                val destinationHeader = StompHeaderAccessor.of()
                    .apply { this.destination = destination }
                    .createHeader()
                val message = StompMessage.Builder()
                    .withHeaders(destinationHeader)
                    .withPayload(data)
                    .create(StompCommand.SEND)
                internalSend(message)
            }
            is ByteArray -> {
                println("LOG_TAG data is String")
                val destinationHeader = StompHeaderAccessor.of()
                    .apply { this.destination = destination }
                    .createHeader()
                val message = StompMessage.Builder()
                    .withHeaders(destinationHeader)
                    .withPayload(data)
                    .create(StompCommand.SEND)
                internalSend(message)
            }
            else -> {
                false
            }
        }
    }

    private fun subscribe(destination: String): Boolean {
        if (topicIds.containsKey(destination)) {
            observe.tryEmit(StompEvent.IsSubscribedError)
            return false
        }
        val generateId = idGenerator.generateId()
        val stompHeaders = StompHeaderAccessor.of()
            .apply {
                this.subscriptionId = generateId
                this.destination = destination
            }
            .createHeader()

        val stompMessage = StompMessage.Builder()
            .withHeaders(stompHeaders)
            .create(StompCommand.SUBSCRIBE)
        val result = internalSend(stompMessage)
        if (result) {
            topicIds[destination] = generateId
        }
        return result
    }

    private fun unsubscribe(destination: String) : Boolean {
        val subscriptionId = topicIds.remove(destination) ?: return false

        val unsubscribeHeader = StompHeaderAccessor.of()
            .apply { this.subscriptionId = subscriptionId }
            .createHeader()

        val unsubscribeStompMessage = StompMessage.Builder()
            .withHeaders(unsubscribeHeader)
            .create(StompCommand.UNSUBSCRIBE)
        return internalSend(unsubscribeStompMessage)
    }

    override fun disconnect(): Boolean {
        println("LOG_TAG OkHttpEngine#disconnect")
        val disconnectStompMessage = StompMessage.Builder()
            .create(StompCommand.DISCONNECT)
        if (internalSend(disconnectStompMessage)) {
            state.value = StompClientState.Disconnected
            connection?.close()
        } else {
            return false
        }
        return true
    }



    private fun internalSend(message: StompMessage): Boolean {
        println("LOG_TAG, internalSend ${message}")
        return connection?.sendMessage(message) ?: false
    }

    inner class InnerWebSocketListener(
        private val onOpenCallback: () -> Unit,
        private val onMessageCallback: (StompMessage) -> Unit
    ) : WebSocketListener() {
        init {
            println("InnerWebSocketListener is initialized")
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            println("pepe1 websocket instance = ${webSocket}")
            println("threadOnOpen${Thread.currentThread().id}")
            val connection = WebSocketConnection(webSocket)
            this@OkHttpEngine.connection = connection
            this@OkHttpEngine.messageHandler = connection
            onOpenCallback()
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val decodedMessage = messageHandler?.handle(bytes.toByteArray())
            decodedMessage?.let { onMessageCallback(it) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            println("threadOnMessage${Thread.currentThread().id}")

            println("LOG_TAG onMessage(webSocket: WebSocket, text={$text}")

            val decodedMessage = messageHandler?.handle(text.toByteArray(Charsets.UTF_8))
            println("decoded message = ${decodedMessage}")
            decodedMessage?.let { onMessageCallback(it) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            println("LOG_TAG InnerWebSocketListener#onClosing websocket!!!!")
            super.onClosing(webSocket, code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            println("LOG_TAG InnerWebSocketListener#onClosed websocket!!!!")
            super.onClosed(webSocket, code, reason)
            observe.tryEmit(StompEvent.Disconnected)
        }
    }

    private fun handleIncome(stompMessage: StompMessage) {
        println("threadhandleIncome${Thread.currentThread().id}")
        println("LOG_TAG OkHttpEngine#handleIncome:[${stompMessage.command}]")
        when (stompMessage.command) {
            StompCommand.CONNECTED -> {
                observe.tryEmit(StompEvent.Connected)
                setupHeartBeat(stompMessage)
            }

            StompCommand.MESSAGE -> {
                val data = stompMessage.payload.toString(Charsets.UTF_8)
                val destination = stompMessage.headers.destination
                println("LOG_TAG messageText=${data})")
                println("LOG_TAG messageDestination=${destination}")
                val isEmitted = observe.tryEmit(
                    StompEvent.Message(
                        destination = destination ?: "",
                        rawData = data,
                        data = null
                    )
                )
                println("LOG_TAG got MESSAGE and isEmitted=${isEmitted}")
            }

            StompCommand.ERROR -> {
                observe.tryEmit(StompEvent.Error)
                println("LOG_TAG got ERROR frame!!!!!")
            }

            else -> println("LOG_TAG Not a server message") // not a server message
        }
    }

    private fun setupHeartBeat(stompMessage: StompMessage) {
        println("LOG_TAG setupHeartBeat")
        val (serverSendInterval, serverReceiveInterval) = stompMessage.headers.heartBeat

        val clientSendInterval = clientConfiguration.heartbeatSendInterval
        val clientReceiveInterval = clientConfiguration.heartbeatReceiveInterval

        println("LOG_TAG serverSendInterval = ${serverSendInterval}")
        println("LOG_TAG serverReceiveInterval = ${serverReceiveInterval}")
        println("LOG_TAG clientSendInterval = ${clientSendInterval}")
        println("LOG_TAG clientReceiveInterval = ${clientReceiveInterval}")

        if (clientSendInterval > 0 && serverReceiveInterval > 0) {
            val interval = max(clientSendInterval, serverReceiveInterval)
            println("LOG_TAG HEARTBEAT SEND interval=$interval")
            connection?.onWriteInactivity(interval, ::sendHeartBeat)
        }

        if (clientReceiveInterval > 0 && serverSendInterval > 0) {
            val interval = max(clientReceiveInterval, serverSendInterval) * HEARTBEAT_MULTIPLIER
            println("LOG_TAG HEARTBEAT RECEIVE interval=$interval")
            connection?.onReceiveInactivity(interval) {
                sendErrorMessage("No messages received in $interval ms.")
                connection?.close()
            }
        }
    }

    private fun sendHeartBeat() {
        val heartbeatStompMessage = StompMessage.Builder()
            .create(StompCommand.HEARTBEAT)
        internalSend(heartbeatStompMessage)
    }

    private fun sendErrorMessage(error: String) {
        val headers = StompHeaderAccessor.of()
            .apply { message(error) }
            .createHeader()

        val stompErrorMessage = StompMessage.Builder()
            .withHeaders(headers)
            .create(StompCommand.ERROR)
        internalSend(stompErrorMessage)
    }
}
