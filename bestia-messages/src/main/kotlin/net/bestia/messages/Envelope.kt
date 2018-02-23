package net.bestia.messages

import java.io.Serializable

open class Envelope(
        val content: Any
) : Serializable