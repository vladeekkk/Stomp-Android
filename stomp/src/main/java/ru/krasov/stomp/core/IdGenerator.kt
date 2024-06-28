package ru.krasov.stomp.core

import java.util.UUID

interface IdGenerator {

    /**
     * Generate a new identifier.
     */
    fun generateId(): String
}

class UuidGenerator : IdGenerator {

    override fun generateId() = UUID.randomUUID().toString()
}