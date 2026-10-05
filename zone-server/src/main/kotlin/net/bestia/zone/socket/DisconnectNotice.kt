package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.Channel
import net.bestia.bnet.proto.DisconnectedProto
import net.bestia.bnet.proto.EnvelopeProto

/** Tells a client why the server ends its connection, then ends it. */
object DisconnectNotice {

  private val LOG = KotlinLogging.logger { }

  fun sendAndClose(channel: Channel, reason: String) {
    try {
      if (channel.isActive) {
        val disconnected = DisconnectedProto.Disconnected
          .newBuilder()
          .setReason(reason)

        val envelope = EnvelopeProto.Envelope.newBuilder()
          .setDisconnected(disconnected)
          .build()

        channel.writeAndFlush(envelope).addListener { channel.close() }
      }
    } catch (e: Exception) {
      LOG.warn(e) { "Failed to send disconnect message, forcing close" }
      channel.close()
    }
  }
}
