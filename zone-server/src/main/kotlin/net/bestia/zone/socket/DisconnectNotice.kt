package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.Channel
import net.bestia.bnet.proto.DisconnectedProto
import net.bestia.bnet.proto.EnvelopeProto
import java.util.concurrent.TimeUnit

/** Tells a client why the server ends its connection, then ends it. */
object DisconnectNotice {

  private val LOG = KotlinLogging.logger { }

  /** Long enough for an honest client to read the reason. */
  private const val CLOSE_TIMEOUT_SECONDS = 2L

  fun sendAndClose(channel: Channel, reason: String) {
    // The close waits for the notice to be written. Nothing the client sends meanwhile may act any more.
    channel.config().isAutoRead = false

    try {
      if (channel.isActive) {
        val disconnected = DisconnectedProto.Disconnected
          .newBuilder()
          .setReason(reason)

        val envelope = EnvelopeProto.Envelope.newBuilder()
          .setDisconnected(disconnected)
          .build()

        channel.writeAndFlush(envelope).addListener { channel.close() }
        // A client that stopped reading never lets the notice out, so the close stops waiting for it.
        channel.eventLoop().schedule({ channel.close() }, CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      }
    } catch (e: Exception) {
      LOG.warn(e) { "Failed to send disconnect message, forcing close" }
      channel.close()
    }
  }
}
