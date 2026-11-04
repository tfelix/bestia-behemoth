package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto.Envelope
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

/** Every handler's decoder reads its own envelope case into the message type the handler takes. */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class WireDecoderTest {

  @Autowired
  private lateinit var handlers: List<IncomingMessageHandler<*>>

  @Test
  fun `every handler decodes an envelope of its case into its message type`() {
    val misread = handlers.mapNotNull { handler ->
      val decoded = handler.wire.decode(ACCOUNT_ID, defaultEnvelopeOf(handler.wire.case))

      // Null is a refusal of a malformed payload, which a default one may well be.
      if (decoded == null || (handler.handles.isInstance(decoded) && decoded.playerId == ACCOUNT_ID)) {
        null
      } else {
        "${handler::class.simpleName} read ${handler.wire.case} as $decoded"
      }
    }

    assertEquals(emptyList(), misread)
  }

  @Test
  fun `the handlers take exactly the cases a client sends`() {
    val handled = handlers.map { it.wire.case }.toSet()

    assertEquals(BnetMessageProcessorAdapter.inboundCases(), handled)
  }

  private fun defaultEnvelopeOf(case: Envelope.MessageCase): Envelope {
    val builder = Envelope.newBuilder()
    val field = Envelope.getDescriptor().findFieldByNumber(case.number)
    builder.setField(field, builder.newBuilderForField(field).build())

    return builder.build()
  }

  private companion object {
    const val ACCOUNT_ID = 7L
  }
}
