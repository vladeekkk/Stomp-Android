package ru.krasov.stomp.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import ru.krasov.stomp.OkHttpStompClientConfiguration
import ru.krasov.stomp.core.model.StompCommand
import ru.krasov.stomp.core.model.StompMessage
import ru.krasov.stomp.core.support.StompHeaderAccessor
import ru.krasov.stomp.state.StompEvent
import kotlin.reflect.KClass

class StompClient(
    private val engine: Engine,
    private val json: Json
) {

    fun connect() {
        println("LOG_TAG StompClient#connect()")
        engine.connect()
    }

    @OptIn(InternalSerializationApi::class)
    fun <T : Any> send(
        destination: String,
        data: T,
        clazz: KClass<T>,
    ) {
        val dataToSend = clazz.serializerOrNull()?.let { serializer ->
            json.encodeToJsonElement(serializer, data).toString()
        }
            ?: data.toString()

        engine.convertAndSend(destination, dataToSend)
    }

    fun disconnect(): Boolean {
        return engine.disconnect()
    }

    fun subscribe(destination: String) {
        engine.convertAndSend(destination, StompCommand.SUBSCRIBE)
    }

    @OptIn(InternalSerializationApi::class)
    fun <T : Any> observeTopic(
        destination: String,
        clazz: KClass<T>
    ): Flow<StompEvent> {
        return engine.observe
            .onEach { println("LOG_TAG StompClient#observeTopic() ${it}") }
            .filter {
                if (it is StompEvent.Message<*>) it.destination == destination
                else true
            }
            .map {
                if (it is StompEvent.Message<*>) {
                    val serializer = clazz.serializer()
                    val data: Any = if (clazz != String::class){
                        Json.decodeFromString(serializer, it.rawData)
                    } else {
                        it.rawData
                    }
                    StompEvent.Message(
                        destination = it.destination,
                        rawData = it.rawData,
                        data = data
                    )
                } else {
                    it
                }
            }
    }

    fun unsubscribe(destination: String): Boolean {
        return engine.convertAndSend(destination, StompCommand.UNSUBSCRIBE)
    }
}
