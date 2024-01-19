package ru.krasov.stomp.okhttp.core

interface Channel {

    fun open()

    fun close()

    fun forceClose()
}
