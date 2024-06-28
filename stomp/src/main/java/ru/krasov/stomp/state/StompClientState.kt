package ru.krasov.stomp.state

sealed interface StompClientState {

    object NotConnected : StompClientState
    class Connecting(val subscription: Subscription) : StompClientState
    object Connected : StompClientState
    class Subscribed(val subscriptions: Set<Subscription>)
    object Disconnected : StompClientState
}

data class Subscription(
    val topicId: String,
    val destination: String
)

data class Message<T>(val destination: String, val data: T)
