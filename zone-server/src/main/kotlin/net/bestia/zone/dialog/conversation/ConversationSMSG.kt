package net.bestia.zone.dialog.conversation

import net.bestia.bnet.proto.ConversationSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.dialog.toBnet
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

/**
 * Somebody is talking to you, and these are the things you may say back.
 *
 * A plain [SMSG] and deliberately **not** an `EntitySMSG`: the client routes those through its entity
 * handling and would try to build an entity for the id. The speaker is metadata here, the same choice
 * `DialogSMSG.sourceEntityId` makes.
 */
data class ConversationSMSG(
  val speakerEntityId: EntityId,
  val speakerName: String,
  val node: ConversationNode,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val conversation = ConversationSmsgProto.ConversationSMSG.newBuilder()
      .setSpeakerEntityId(speakerEntityId)
      .setSpeakerName(speakerName)
      .setSpeech(node.speech.toBnet())

    node.options.forEach { conversation.addOptions(it.toBnet()) }

    return EnvelopeProto.Envelope.newBuilder()
      .setConversation(conversation.build())
      .build()
  }

  private fun Line.toBnet(): ConversationSmsgProto.Line {
    val line = ConversationSmsgProto.Line.newBuilder().setKey(key)
    args.forEach { (name, arg) -> line.putArgs(name, arg.toBnet()) }

    return line.build()
  }

  private fun ConversationOption.toBnet(): ConversationSmsgProto.ConversationOption {
    return ConversationSmsgProto.ConversationOption.newBuilder()
      .setTopicId(topicId)
      .setLine(line.toBnet())
      .setKind(kind.toBnet())
      .build()
  }

  // No `else`: a new OptionKind must fail to compile here rather than serialize as TALK and leave a
  // player clicking an option that silently does nothing.
  private fun OptionKind.toBnet(): ConversationSmsgProto.OptionKind = when (this) {
    OptionKind.TALK -> ConversationSmsgProto.OptionKind.TALK
    OptionKind.ACTION -> ConversationSmsgProto.OptionKind.ACTION
    OptionKind.BACK -> ConversationSmsgProto.OptionKind.BACK
    OptionKind.END -> ConversationSmsgProto.OptionKind.END
  }
}
