package ru.krasov.stomp


import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.krasov.stomp.core.StompClient
import ru.krasov.stomp.okhttp.OkHttpEngine
import ru.krasov.stomp.okhttp.WebSocketFactory
import ru.krasov.stomp.state.StompEvent

internal class SubscribeIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var clientWebSocket: WebSocket

    private var shouldSendMessageToDestination = false
    private var shouldSendMessageToOtherDestination = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = OkHttpClient()

        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                println("RecordedRequest requestUrl = ${request.requestUrl}")
                println("RecordedRequest path = ${request.path}")
                println("RecordedRequest headers = ${request.headers.names()}")

                if (request.path.equals("/")) {
                    return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                            println("Server: WebSocket connection opened")
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            println("Server received message: $text")
                            when {
                                text.contains("CONNECT") -> webSocket.send(CONNECTED_FRAME)
                                text.contains("SUBSCRIBE")  -> {
                                    if (shouldSendMessageToDestination) {
                                        webSocket.send(MESSAGE_FRAME)
                                    }
                                    if (shouldSendMessageToOtherDestination) {
                                        webSocket.send(MESSAGE_FRAME_DEST)
                                    }
                                }
                            }
                        }
                    })
                }
                return MockResponse().setResponseCode(404);
            }
        }
    }


    @After
    fun tearDown() {
        clientWebSocket.close(1000, "Test completed")
    }


    @Test
    fun `SUBSCRIBE twice to one destination`() = runTest(testDispatcher) {
        shouldSendMessageToDestination = false
        shouldSendMessageToOtherDestination = false
        val factory = object : WebSocketFactory {
            override fun createWebsocket(
                okHttpClient: OkHttpClient,
                listener: WebSocketListener,
                request: Request
            ): WebSocket {
                return client.newWebSocket(request, listener).also { clientWebSocket = it }
            }
        }
        val engine = OkHttpEngine(
            client, factory,
            clientConfiguration = OkHttpStompClientConfiguration(
                host = mockWebServer.hostName, port = mockWebServer.port
            ),
        )

        val stompClient = StompClient(
            engine = engine,
            json = Json
        )

        engine.observe.test {
            stompClient.connect()
            val connectedItem = awaitItem()
            assert(connectedItem == StompEvent.Connected)
            stompClient.subscribe("/kek")
            stompClient.subscribe("/kek")
            val errorFromSubscription = awaitItem()
            assertTrue(errorFromSubscription == StompEvent.IsSubscribedError)
        }
    }

    @Test
    fun `SUBSCRIBE and receive message for destination`() = runTest(testDispatcher) {
        shouldSendMessageToDestination = true
        shouldSendMessageToOtherDestination = false
        val factory = object : WebSocketFactory {
            override fun createWebsocket(
                okHttpClient: OkHttpClient,
                listener: WebSocketListener,
                request: Request
            ): WebSocket {
                return client.newWebSocket(request, listener).also { clientWebSocket = it }
            }
        }
        val engine = OkHttpEngine(
            client, factory,
            clientConfiguration = OkHttpStompClientConfiguration(
                host = mockWebServer.hostName, port = mockWebServer.port
            ),
        )

        val stompClient = StompClient(
            engine = engine,
            json = Json
        )

        engine.observe.test {
            stompClient.connect()
            val connectedItem = awaitItem()
            assert(connectedItem == StompEvent.Connected)
            stompClient.subscribe("/kek")
            val messageItem = awaitItem()
            assertTrue(messageItem is StompEvent.Message<*>)
            val message = (messageItem as StompEvent.Message<*>).rawData
            assertTrue(message.contains( "hello from server!"))
        }
    }


    @Test
    fun `observe topic from client`() = runTest(testDispatcher) {
        shouldSendMessageToDestination = true
        shouldSendMessageToOtherDestination = true
        val factory = object : WebSocketFactory {
            override fun createWebsocket(
                okHttpClient: OkHttpClient,
                listener: WebSocketListener,
                request: Request
            ): WebSocket {
                return client.newWebSocket(request, listener).also { clientWebSocket = it }
            }
        }
        val engine = OkHttpEngine(
            client, factory,
            clientConfiguration = OkHttpStompClientConfiguration(
                host = mockWebServer.hostName, port = mockWebServer.port
            ),
        )

        val stompClient = StompClient(
            engine = engine,
            json = Json
        )

        stompClient.observeTopic("/kek", String::class).test {
            stompClient.connect()
            val connectedItem = awaitItem()
            assertTrue(connectedItem is StompEvent.Connected)
            stompClient.subscribe("/kek")
            val messageItem = awaitItem()
            println("item = $messageItem")
            assertTrue(messageItem is StompEvent.Message<*>)
            val message = (messageItem as StompEvent.Message<*>).rawData
            assertTrue(message.contains( "hello from server!"))
        }
    }

    @Test
    fun `subscribe to topic A, observe topic B`() = runTest(testDispatcher) {
        shouldSendMessageToDestination = true
        shouldSendMessageToOtherDestination = true
        val factory = object : WebSocketFactory {
            override fun createWebsocket(
                okHttpClient: OkHttpClient,
                listener: WebSocketListener,
                request: Request
            ): WebSocket {
                return client.newWebSocket(request, listener).also { clientWebSocket = it }
            }
        }
        val engine = OkHttpEngine(
            client, factory,
            clientConfiguration = OkHttpStompClientConfiguration(
                host = mockWebServer.hostName, port = mockWebServer.port
            ),
        )

        val stompClient = StompClient(
            engine = engine,
            json = Json
        )

        stompClient.observeTopic("/lol", String::class).test {
            stompClient.connect()
            val connectedItem = awaitItem()
            assertTrue(connectedItem is StompEvent.Connected)
            stompClient.subscribe("/kek")
            expectNoEvents()
        }
    }

    @Test
    fun `subscribe to topics A and B, observe topic B`() = runTest(testDispatcher) {
        shouldSendMessageToDestination = true
        shouldSendMessageToOtherDestination = true
        val factory = object : WebSocketFactory {
            override fun createWebsocket(
                okHttpClient: OkHttpClient,
                listener: WebSocketListener,
                request: Request
            ): WebSocket {
                return client.newWebSocket(request, listener).also { clientWebSocket = it }
            }
        }
        val engine = OkHttpEngine(
            client, factory,
            clientConfiguration = OkHttpStompClientConfiguration(
                host = mockWebServer.hostName, port = mockWebServer.port
            ),
        )

        val stompClient = StompClient(
            engine = engine,
            json = Json
        )

        stompClient.observeTopic("/lol", String::class).test {
            stompClient.connect()
            val connectedItem = awaitItem()
            assertTrue(connectedItem is StompEvent.Connected)
            stompClient.subscribe("/kek")
            stompClient.subscribe("/lol")
            val item = awaitItem()
            assertTrue(item is StompEvent.Message<*>)
        }
    }

    @Test
    fun `engine works correct when subscribed to many topics`() = runTest(testDispatcher) {
        val factory = object : WebSocketFactory {
            override fun createWebsocket(
                okHttpClient: OkHttpClient,
                listener: WebSocketListener,
                request: Request
            ): WebSocket {
                return client.newWebSocket(request, listener).also { clientWebSocket = it }
            }
        }
        val engine = OkHttpEngine(
            client, factory,
            clientConfiguration = OkHttpStompClientConfiguration(
                host = mockWebServer.hostName, port = mockWebServer.port
            ),
        )

        val stompClient = StompClient(
            engine = engine,
            json = Json
        )

        engine.observe.test {
            stompClient.connect()
            val connectedItem = awaitItem()
            assertTrue(connectedItem is StompEvent.Connected)
            shouldSendMessageToDestination = true
            shouldSendMessageToOtherDestination = false
            stompClient.subscribe("/kek")
            val kekItem = awaitItem()
            assertTrue(kekItem is StompEvent.Message<*>)
            shouldSendMessageToDestination = false
            shouldSendMessageToOtherDestination = true
            stompClient.subscribe("/lol")
            val itemLol = awaitItem()
            assertTrue(itemLol is StompEvent.Message<*>)
        }
    }
}
