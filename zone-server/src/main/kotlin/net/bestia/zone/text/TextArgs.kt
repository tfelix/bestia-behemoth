package net.bestia.zone.text

import net.bestia.bnet.proto.DialogSmsgProto

/**
 * The one place a [TextArg] becomes wire.
 *
 * Shared rather than written twice because two messages now carry these - the one-shot popup and a
 * conversation line - and a second copy is how one of them comes to serialize a variant as something
 * else. There is no `else` branch: a new variant must fail to compile here.
 */
internal fun TextArg.toBnet(): DialogSmsgProto.DialogArg {
  val builder = DialogSmsgProto.DialogArg.newBuilder()

  when (this) {
    is TextArg.Text -> builder.setText(value)
    is TextArg.Number -> builder.setNumber(value)
    is TextArg.Entity -> builder.setEntityId(entityId)
    is TextArg.Item -> builder.setItemId(itemId)
    is TextArg.Skill -> builder.setSkillId(skillId)
    is TextArg.Token -> builder.setToken(key)
    is TextArg.Name -> builder.setName(value)
  }

  return builder.build()
}
