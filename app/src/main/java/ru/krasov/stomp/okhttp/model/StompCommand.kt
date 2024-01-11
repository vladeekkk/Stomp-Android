package ru.krasov.stomp.okhttp.model

/**
 * Represents a STOMP command.
 * Only the SEND, MESSAGE, and ERROR frames MAY have a body. All other frames MUST NOT have a body.
 */
enum class StompCommand(
    val isBodyAllowed: Boolean = false,
    val isDestinationRequired: Boolean = false
) {
    // client
    CONNECT,
    DISCONNECT,
    SEND(isBodyAllowed = true, isDestinationRequired = true),
    SUBSCRIBE(isDestinationRequired = true),
    UNSUBSCRIBE,

    // server
    CONNECTED,
    MESSAGE(isBodyAllowed = true, isDestinationRequired = true),
    ERROR(isBodyAllowed = true),

    // heartbeat
    HEARTBEAT
}