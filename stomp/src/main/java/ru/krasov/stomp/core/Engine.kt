package ru.krasov.stomp.core

import kotlinx.coroutines.flow.Flow
import ru.krasov.stomp.state.StompEvent

interface Engine {

    val observe: Flow<StompEvent>

    fun connect()

    fun <T> convertAndSend(destination: String, data: T): Boolean

    fun disconnect(): Boolean
}
