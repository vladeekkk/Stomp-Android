package ru.krasov.stomp

import app.cash.turbine.test
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
import org.junit.Before
import org.junit.Test
import ru.krasov.stomp.core.StompClient
import ru.krasov.stomp.okhttp.OkHttpEngine
import ru.krasov.stomp.okhttp.WebSocketFactory
import ru.krasov.stomp.state.StompEvent

internal class SendConnectReceiveErrorIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var clientWebSocket: WebSocket

    private var shouldSendError: Boolean = false

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
                            // Send a message back to the client
                            if (shouldSendError) {
                                webSocket.send(ERROR_FRAME)
                                println("Server sent ERROR frame")
                            } else {
                                println("Server DID NOT send ERROR frame")
                            }
                        }
                    })
                }
                return MockResponse().setResponseCode(404);
            }
        }
    }


    @Test
    fun `send CONNECT and receive ERROR`() = runTest {
        shouldSendError = true
        val (stompClient, engine) = initStompClientAndEngine()
        stompClient.connect()
        engine.observe.test {
            val item = awaitItem()
            assert(item == StompEvent.Error)
        }
    }

    @Test
    fun `send CONNECT and receive nothing`() = runTest {
        shouldSendError = false
        val (stompClient, engine) = initStompClientAndEngine()
        stompClient.connect()
        engine.observe.test {
            expectNoEvents()
        }
    }

    private fun initStompClientAndEngine(): Pair<StompClient, OkHttpEngine> {
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
                host = mockWebServer.hostName,
                port = mockWebServer.port
            )
        )
        val stompClient = StompClient(
            engine = engine,
            json = Json
        )
        return stompClient to engine
    }
}
