package bestia.messages

/**
 * Messages coming from the client are wrapped in this envelope and contain
 * the payload.
 */
class ClientFromMessageEnvelope(content: Any) : Envelope(content)