package net.bestia.messages

class ClientToMessageEnvelope(val clientAccountId: Long,
                              content: Any) : Envelope(content)