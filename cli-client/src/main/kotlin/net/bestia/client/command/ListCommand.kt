package net.bestia.client.command

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ListAvailableBestiasProto
import java.lang.IllegalStateException

class ListCommand(
  private val session: Session
) : CliCommand {
  override val name = "list"
  override val usage = "Usage: list [bestia]"
  override val description = "Lists the resources to the client owned by the player."

  override fun execute(tokens: List<String>) {
    if (tokens.size != 2) {
      session.print(usage)
      return
    }

    val envelope = when (val listCmd = tokens[1]) {
      "bestia" -> {
        val listBestias = ListAvailableBestiasProto.ListAvailableBestias.newBuilder()

        EnvelopeProto.Envelope.newBuilder()
          .setListBestias(listBestias)
          .build()
      }

      else -> throw IllegalStateException("Unknown list sub-command: $listCmd")
    }

    session.sendEnvelope(envelope)
  }
}