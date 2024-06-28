package ru.krasov.stomp.state

interface StompEvent {

    object Connected : StompEvent

    data class Message<T>(
        val destination: String,
        val rawData: String,
        val data: T?
    ) : StompEvent

    object IsSubscribedError : StompEvent

    object Error : StompEvent
    object Disconnected: StompEvent
}
