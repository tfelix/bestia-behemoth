package net.bestia.messages

/**
 * Contains messages which will get routed and delivered inside the akka
 * system.
 */
class Envelope(
        val identifier: String,
        val content: Any
) {
  override fun toString(): String {
    return "Envelope[$content]"
  }

  fun isEnvelope(identifier: String) : Boolean {
    return identifier == identifier
  }

  companion object {
    fun toEntity(content: Any) : Envelope {
      return Envelope(ENTITY_ENVELOPE, content)
    }
  }
}

val ENTITY_ENVELOPE = "entity"