package ru.krasov.stomp.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krasov.stomp.core.model.StompCommand
import ru.krasov.stomp.core.model.StompMessage
import ru.krasov.stomp.core.support.StompHeaderAccessor
import ru.krasov.stomp.core.support.StompMessageEncoder

class StompMessageEncoderTest {

    private val encoder = StompMessageEncoder()

    @Test
    fun `encode frame with no headers and no body`() {
        val frame = StompMessage.Builder()
            .withPayload(ByteArray(0))
            .create(StompCommand.DISCONNECT)

        assertEquals("DISCONNECT\n\n\u0000", String(encoder.encode(frame)))
    }

    @Test
    fun `encode frame with headers`() {
        val headers = StompHeaderAccessor.of().apply {
            acceptVersion = "1.2"
            host = "github.org"
        }.createHeader()

        val frame = StompMessage.Builder()
            .withPayload(ByteArray(0))
            .withHeaders(headers)
            .create(StompCommand.CONNECT)

        val frameString = String(encoder.encode(frame))
        assertTrue(
            "CONNECT\naccept-version:1.2\nhost:github.org\n\n\u0000" == frameString ||
                    "CONNECT\nhost:github.org\naccept-version:1.2\n\n\u0000" == frameString
        )
    }

    @Test
    fun `encode frame with headers that should be escaped`() {
        val headers = StompHeaderAccessor.of("a:\r\n\\b" to "alpha:bravo\r\n\\")
            .createHeader()

        val frame = StompMessage.Builder()
            .withPayload(ByteArray(0))
            .withHeaders(headers)
            .create(StompCommand.DISCONNECT)

        assertEquals(
            "DISCONNECT\na\\c\\r\\n\\\\b:alpha\\cbravo\\r\\n\\\\\n\n\u0000",
            String(encoder.encode(frame))
        )
    }

    @Test
    fun `encode frame with headers body`() {
        val headers = StompHeaderAccessor.of(("a" to "alpha")).apply {
            destination = "destination"
        }.createHeader()

        val frame = StompMessage.Builder()
            .withPayload("Message body")
            .withHeaders(headers)
            .create(StompCommand.SEND)

        assertEquals(
            "SEND\na:alpha\ndestination:destination\ncontent-length:12\n\nMessage body\u0000",
            String(encoder.encode(frame))
        )
    }

    @Test
    fun `encode frame with content length present`() {
        val headers = StompHeaderAccessor.of().apply {
            contentLength = 22
            destination = "destination"
        }.createHeader()

        val frame = StompMessage.Builder()
            .withPayload("Message body")
            .withHeaders(headers)
            .create(StompCommand.SEND)

        assertEquals(
            "SEND\ndestination:destination\ncontent-length:12\n\nMessage body\u0000",
            String(encoder.encode(frame))
        )
    }
}
