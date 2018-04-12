package net.bestia.messages

import java.io.Serializable

abstract class Envelope(
        val content: Any
) : Serializable