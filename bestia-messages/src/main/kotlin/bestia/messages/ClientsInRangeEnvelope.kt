package bestia.messages

class ClientsInRangeEnvelope(
        val positionEntityId: Long,
        content: Any
) : Envelope(content)