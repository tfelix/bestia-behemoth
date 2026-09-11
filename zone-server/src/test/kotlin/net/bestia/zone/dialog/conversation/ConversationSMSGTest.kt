package net.bestia.zone.dialog.conversation

import net.bestia.bnet.proto.DialogSmsgProto
import net.bestia.zone.dialog.DialogArg
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That a conversation survives the wire, and in particular that the two new argument kinds do.
 *
 * `token` and `name` are the whole reason generated history can be translated at all - one carries a
 * word to look up and the other a word not to. They serialize into the same `oneof` as five older
 * kinds, so a mapping that quietly wrote one as `text` would still round-trip a string and still show a
 * player something plausible, in English, forever.
 */
class ConversationSMSGTest {

  @Test
  fun `a line carries its key and its typed arguments`() {
    val message = ConversationSMSG(
      speakerEntityId = 77L,
      speakerName = "Alden",
      node = ConversationNode(
        speech = Line(
          "HISTORY_ERUPTION_1",
          mapOf(
            "place" to DialogArg.Name("Karth"),
            "ago" to DialogArg.Token("AGO_AGES"),
            "year" to DialogArg.Number(312),
          )
        ),
        options = emptyList(),
      )
    )

    val sent = message.toBnetEnvelope().conversation

    assertEquals(77L, sent.speakerEntityId)
    assertEquals("Alden", sent.speakerName)
    assertEquals("HISTORY_ERUPTION_1", sent.speech.key)

    val args = sent.speech.argsMap
    assertEquals("Karth", args.getValue("place").name)
    assertEquals("AGO_AGES", args.getValue("ago").token)
    assertEquals(312L, args.getValue("year").number)

    // The `oneof` case, not just the value - a name written as text reads identically on this side and
    // is exactly the mistake worth catching.
    assertEquals(DialogSmsgProto.DialogArg.ValueCase.NAME, args.getValue("place").valueCase)
    assertEquals(DialogSmsgProto.DialogArg.ValueCase.TOKEN, args.getValue("ago").valueCase)
  }

  @Test
  fun `every option kind survives the crossing`() {
    val options = OptionKind.entries.mapIndexed { index, kind ->
      ConversationOption(index, Line("ASK_$index"), kind)
    }

    val sent = ConversationSMSG(1L, "Alden", ConversationNode(Line("TALK_GREETING"), options)).toBnetEnvelope()

    assertEquals(OptionKind.entries.size, sent.conversation.optionsCount)
    sent.conversation.optionsList.forEachIndexed { index, option ->
      assertEquals(index, option.topicId)
      assertEquals(OptionKind.entries[index].name, option.kind.name, "an option kind changed meaning on the wire")
    }
  }

  @Test
  fun `a conversation with nothing to offer still says something`() {
    val sent = ConversationSMSG(1L, "Alden", ConversationNode(Line("TALK_GOODBYE"), emptyList())).toBnetEnvelope()

    assertEquals("TALK_GOODBYE", sent.conversation.speech.key)
    assertTrue(sent.conversation.optionsList.isEmpty())
  }
}
