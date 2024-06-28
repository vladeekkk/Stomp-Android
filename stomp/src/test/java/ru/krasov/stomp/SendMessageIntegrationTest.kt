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

internal class SendMessageIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var clientWebSocket: WebSocket

    private var receivedMessageFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

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
                                text.contains("SEND")  -> {
                                    val tryEmit = receivedMessageFlow.tryEmit(text)
                                    println("HEY BRO_1 ${tryEmit}")
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
    fun `connect and SEND message`() = runTest(testDispatcher) {

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
            val item = awaitItem()
            assert(item == StompEvent.Connected)
        }

        receivedMessageFlow.test {
            stompClient.send("/kek", User("Vladislav"), User::class)
            val receivedMessage = awaitItem()
            assertTrue(receivedMessage.contains("destination:/kek"))
            assertTrue(receivedMessage.contains("content-length:20"))
            assertTrue(receivedMessage.contains("{\"name\":\"Vladislav\"}"))
        }
    }

    @Serializable
    private data class User(val name: String)
}
