package net.bestia.client.command

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PingOuterClass

class PingCommand(
  private val session: Session
) : CliCommand {
  override val name = "ping"
  override val description = "pings the server and waits for a pong response. Useful for checking connectivity."
  override val usage = "ping"

  override fun execute(tokens: List<String>) {
    // Create a protobuf message
    val message = PingOuterClass.Ping.newBuilder()
      .build()

    val envelope = EnvelopeProto.Envelope.newBuilder()
      .setPing(message)
      .build()

    session.sendEnvelope(envelope)
  }
}