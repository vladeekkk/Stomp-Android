package ru.krasov.stomp.okhttp.core

import ru.krasov.stomp.okhttp.model.StompHeader
import ru.krasov.stomp.okhttp.model.StompMessage

typealias StompListener = (StompMessage) -> Unit

/**
 * Interface use for subscribe and unsubscribe to STOMP queue.
 */
interface StompSubscriber {

    /**
     * Subscribe to given destination with headers.
     */
    fun subscribe(destination: String, headers: StompHeader?, listener: StompListener)

    /**
     * Unsubscribe from given destination.
     */
    fun unsubscribe(destination: String)
}
