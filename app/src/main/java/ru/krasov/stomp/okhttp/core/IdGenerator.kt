package ru.krasov.stomp.okhttp.core

import java.util.UUID

interface IdGenerator {

    /**
     * Generate a new identifier.
     */
    fun generateId(): String
}

class IdGeneratorImpl : IdGenerator {
    override fun generateId(): String {
        return UUID.randomUUID().toString()
    }
}
