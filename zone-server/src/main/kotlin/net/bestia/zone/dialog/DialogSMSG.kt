package net.bestia.zone.dialog

import net.bestia.bnet.proto.DialogSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

/**
 * Tells one client to show a dialog. Carries no text at all - only [dialogId], which the client
 * resolves against its own translation source. See [DialogService] for how to send one; nothing
 * should construct this directly.
 *
 * This is account-scoped, not entity-scoped, which is why it implements plain [SMSG] rather than
 * [net.bestia.zone.message.EntitySMSG]: the client must not route it through its entity handling.
 * [sourceEntityId] is informative metadata for the presentation layer (who is speaking) and may
 * point at an entity the receiving client does not even know about.
 */
data class DialogSMSG(
  val dialogId: Int,
  val type: DialogType,
  val args: Map<String, DialogArg> = emptyMap(),
  val sourceEntityId: EntityId? = null,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val dialog = DialogSmsgProto.DialogSMSG.newBuilder()
      .setDialogId(dialogId)
      .setType(type.toBnet())

    args.forEach { (name, arg) -> dialog.putArgs(name, arg.toBnet()) }
    sourceEntityId?.let { dialog.setSourceEntityId(it) }

    return EnvelopeProto.Envelope.newBuilder()
      .setDialog(dialog.build())
      .build()
  }

  // No `else` branch on either mapping: a new DialogType or DialogArg variant must fail to compile
  // here rather than silently serialize as something wrong.
  private fun DialogType.toBnet(): DialogSmsgProto.DialogType = when (this) {
    DialogType.CONFIRM -> DialogSmsgProto.DialogType.CONFIRM
  }

  private fun DialogArg.toBnet(): DialogSmsgProto.DialogArg {
    val builder = DialogSmsgProto.DialogArg.newBuilder()

    when (this) {
      is DialogArg.Text -> builder.setText(value)
      is DialogArg.Number -> builder.setNumber(value)
      is DialogArg.Entity -> builder.setEntityId(entityId)
      is DialogArg.Item -> builder.setItemId(itemId)
      is DialogArg.Skill -> builder.setSkillId(skillId)
    }

    return builder.build()
  }
}
