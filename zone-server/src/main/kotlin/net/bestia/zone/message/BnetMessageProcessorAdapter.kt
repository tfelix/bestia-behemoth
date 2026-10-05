package net.bestia.zone.message

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Receives Bnet protobuf messages, reads each with its handler's [WireDecoder] and hands it to the
 * [InMessageProcessor].
 *
 * Refuses to start when a client-to-server case has no handler, so a message added to the envelope cannot
 * silently go unanswered.
 */
@Component
class BnetMessageProcessorAdapter(
  handlers: List<IncomingMessageHandler<*>>,
  private val inMessageProcessor: InMessageProcessor,
) {

  private val decoders: Map<MessageCase, WireDecoder<*>> = handlers.associate { it.wire.case to it.wire }

  init {
    val shared = handlers.groupBy { it.wire.case }.filterValues { it.size > 1 }.keys
    require(shared.isEmpty()) { "Envelope cases with more than one handler: $shared" }

    val unhandled = inboundCases() - decoders.keys
    require(unhandled.isEmpty()) { "Envelope cases a client sends that no handler takes: $unhandled" }
  }

  @EventListener
  fun handleMessageEnvelopeReceived(event: MessageEnvelopeReceivedEvent) {
    val accountId = event.senderAccountId
    val envelope = event.envelope

    val decoder = decoders[envelope.messageCase] ?: throw UnknownBnetMessageException(envelope)
    val internalMessage = decoder.decode(accountId, envelope)

    if (internalMessage == null) {
      LOG.warn { "handleMessageEnvelopeReceived: dropping unparsable message from account $accountId" }
      return
    }

    inMessageProcessor.submit(internalMessage)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Cases only the server sends, whose message type does not say so with an `SMSG` suffix. */
    private val SERVER_TO_CLIENT = setOf(
      MessageCase.OPERATION_SUCCESS, MessageCase.OPERATION_ERROR, MessageCase.DISCONNECTED,
      MessageCase.AUTHENTICATION_SUCCESS, MessageCase.PONG, MessageCase.MASTER,
    )

    /** Every case a client may send once logged in. `AUTHENTICATION` is the handshake's. */
    fun inboundCases(): Set<MessageCase> {
      return MessageCase.values()
        .filter { it != MessageCase.MESSAGE_NOT_SET && it != MessageCase.AUTHENTICATION }
        .filter { it !in SERVER_TO_CLIENT }
        .filterNot { Envelope.getDescriptor().findFieldByNumber(it.number).messageType.name.endsWith("SMSG") }
        .toSet()
    }
  }
}
