package ru.krasov.stomp

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.krasov.stomp.core.StompClient
import ru.krasov.stomp.okhttp.OkHttpEngine
import ru.krasov.stomp.okhttp.WebSocketFactory
import ru.krasov.stomp.state.StompEvent

class UnsubscribeIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var clientWebSocket: WebSocket

    private var shouldSendMessageToDestination = false

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
                                }
                                text.contains("UNSUBSCRIBE") -> {
                                    webSocket.send(MESSAGE_FRAME)
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
    fun `unsubscribe from nonexistent topic`() {
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
        stompClient.connect()
        stompClient.subscribe("/kek")
        assertFalse(stompClient.unsubscribe("/otherTopic"))
    }

    @Test
    fun `receive no messages after unsubscribe`() = runTest(testDispatcher) {
        shouldSendMessageToDestination = false
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
            Thread.sleep(300)
            shouldSendMessageToDestination = true
            stompClient.unsubscribe("/kek")
            expectNoEvents()
        }
    }
}
