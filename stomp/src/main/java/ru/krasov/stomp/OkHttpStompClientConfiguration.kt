package ru.krasov.stomp

data class OkHttpStompClientConfiguration(
    val acceptVersion: String = ACCEPT_VERSION,
    val host: String,
    val port: Int,
    val login: String? = null,
    val passcode: String? = null,
    val heartbeatSendInterval: Long = 0,
    val heartbeatReceiveInterval: Long = 0
) {

    companion object {

        private const val ACCEPT_VERSION = "1.1,1.2"
    }

    val shouldSendHeartBeat: Boolean
        get() = heartbeatSendInterval > 0 && heartbeatReceiveInterval > 0

    val headerBeatPair: Pair<Long, Long>
        get() = heartbeatSendInterval to heartbeatReceiveInterval
}
