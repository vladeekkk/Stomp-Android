package ru.krasov.stomp.okhttp.core

import ru.krasov.stomp.okhttp.model.StompMessage

interface Connection {

    /**
     * Send the given message.
     * @param message the message
     * @return true if the message was enqueued.
     */
    fun sendMessage(message: StompMessage): Boolean

    /**
     * Register a task to invoke after a period of read inactivity.
     * @param runnable the task to invoke
     * @param duration the amount of inactive time in milliseconds
     */
    fun onReceiveInactivity(duration: Long, runnable: () -> Unit)

    /**
     * Register a task to invoke after a period of write inactivity.
     * @param runnable the task to invoke
     * @param duration the amount of inactive time in milliseconds
     */
    fun onWriteInactivity(duration: Long, runnable: () -> Unit)

    /**
     * Force close the connection.
     */
    fun forceClose()

    /**
     * Close the connection.
     */
    fun close()
}
