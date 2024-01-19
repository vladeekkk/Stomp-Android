package ru.krasov.stomp.okhttp.client

data class OkHttpStompClientConfiguration(
    val host: String,
    val heartbeatSendInterval: Long = 0,
    val heartbeatReceiveInterval: Long = 0
) {

    val shouldSendHeartBeat: Boolean
        get() = heartbeatSendInterval > 0 && heartbeatReceiveInterval > 0

    val headerBeatPair: Pair<Long, Long>
        get() = heartbeatSendInterval to heartbeatReceiveInterval
}
