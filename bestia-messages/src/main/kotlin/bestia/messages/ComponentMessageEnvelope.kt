package bestia.messages

/**
 * Messages inside this component envelope are delivered to the component actor of the entity actor.
 */
class ComponentMessageEnvelope(
        val entityId: Long,
        val componentId: Long,
        content: Any
) : Envelope(content) {
  override fun toString(): String {
    return "ComponentEnvelope[cId: $componentId, content: $content]"
  }
}