package ru.krasov.stomp.core

import ru.krasov.stomp.core.model.StompMessage

interface MessageHandler {

    /**
     * Convert given raw data byte array to stomp message
     */
    fun handle(data: ByteArray): StompMessage?
}