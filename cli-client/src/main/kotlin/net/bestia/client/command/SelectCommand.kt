package net.bestia.client.command

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SelectActiveEntityProto

class SelectCommand(
  private val session: Session
) : CliCommand {
  override val name = "select"
  override val usage = "select <entity_id>"
  override val description = "Selects the currently active Bestia to receive network updates"

  override fun execute(tokens: List<String>) {
    if (tokens.size != 2) {
      session.print(usage)
      return
    }

    val entityId = try {
      tokens[1].toLong()
    } catch (e: NumberFormatException) {
      session.print("Invalid entity id. Use integers.")
      return
    }

    val selectActiveEntity = SelectActiveEntityProto.SelectActiveEntity.newBuilder()
      .setEntityId(entityId)
      .build()

    val envelope = EnvelopeProto.Envelope.newBuilder()
      .setSelectActiveEntity(selectActiveEntity)
      .build()

    session.sendEnvelope(envelope)
  }
}