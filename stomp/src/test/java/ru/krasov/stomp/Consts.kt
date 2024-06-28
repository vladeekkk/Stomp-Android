package ru.krasov.stomp

private const val receipt = "receipt-id:message-12345\n"
private const val contentType = "content-type:text/plain\n"
private const val errorPayload: String = "\nSadge.\n"

internal const val ERROR_FRAME = "ERROR\n$receipt$contentType$errorPayload\n\u0000"

private const val acceptVersion = "accept-version:1.1\n"

internal const val CONNECTED_FRAME = "CONNECTED\n$acceptVersion\n\u0000"

internal const val MESSAGE_FRAME = "MESSAGE\ndestination:/kek\n\nhello from server!\n\n\u0000"

internal const val MESSAGE_FRAME_DEST = "MESSAGE\ndestination:/lol\n\nhello from server!\n\n\u0000"
