package ru.krasov.stomp.okhttp.core

import ru.krasov.stomp.okhttp.model.StompMessage

interface MessageHandler {

    /**
     * Convert given raw data byte array to stomp message
     */
    fun handle(data: ByteArray): StompMessage?
}
