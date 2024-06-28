package ru.krasov.example

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import ru.krasov.stomp.OkHttpStompClientConfiguration
import ru.krasov.stomp.core.StompClient
import ru.krasov.stomp.okhttp.OkHttpEngine
import ru.krasov.stomp.state.StompEvent
import ru.krasov.stomp.okhttp.R as MainActivityR

private const val ANDROID_EMULATOR_LOCALHOST = "10.0.2.2"
private const val SERVER_PORT = 8765
private const val TERMINATE_MESSAGE_SYMBOL = "\u0000"

class MainActivity : AppCompatActivity() {

    val LifecycleOwner.lifecycleScope: LifecycleCoroutineScope
        get() = lifecycle.coroutineScope

    lateinit var client: StompClient

    val connectListener: (View) -> Unit = { client.connect() }

    val sendListener: (View) -> Unit = {
        println("SEND ${System.currentTimeMillis()}")

        client.send(destination = "/mytopic", data = User("John"), User::class)
        client.send(destination = "/kek", data = "test from vladek", String::class)
    }

    val subscribeListener: (View) -> Unit = {
        client.subscribe(destination = "/kek")
    }

    val unsubscribeListener: (View) -> Unit = {
        client.unsubscribe(destination = "/kek")
    }

    val disconnectListener: (View) -> Unit = { client.disconnect() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(MainActivityR.layout.main_activity)


        val okHttpClient = OkHttpClient()

        client = StompClient(
            engine = OkHttpEngine(
                okHttpClient = okHttpClient,
                clientConfiguration = OkHttpStompClientConfiguration(
                    host = ANDROID_EMULATOR_LOCALHOST,
                    port = SERVER_PORT,
                ),
            ),
            json = Json
        )
        initUi()
    }

    override fun onResume() {
        super.onResume()
        client.connect()
        lifecycleScope.launch(Dispatchers.IO) {
            client
                .observeTopic("/kek", User::class)
                .filter { it is StompEvent.Message<*> }
                .collect {
                    println("LOG_TAG ${(it as StompEvent.Message<*>).data}")
                }
        }
    }

    private fun initUi() {
        val btnConnect: Button = findViewById(MainActivityR.id.connect_button)
        val btnSend: Button = findViewById(MainActivityR.id.send_button)
        val btnSubscribe: Button = findViewById(MainActivityR.id.subscribe_button)
        val btnUnsubscribe: Button = findViewById(MainActivityR.id.unsubscribe_button)
        val btnDisconnect: Button = findViewById(MainActivityR.id.disconnect_button)
        btnConnect.setOnClickListener(connectListener)
        btnSend.setOnClickListener(sendListener)
        btnDisconnect.setOnClickListener(disconnectListener)
        btnSubscribe.setOnClickListener(subscribeListener)
        btnUnsubscribe.setOnClickListener(unsubscribeListener)
    }
}
