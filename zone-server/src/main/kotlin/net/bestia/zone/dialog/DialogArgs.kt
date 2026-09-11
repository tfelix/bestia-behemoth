package net.bestia.zone.dialog

import net.bestia.bnet.proto.DialogSmsgProto

/**
 * The one place a [DialogArg] becomes wire.
 *
 * Shared rather than written twice because two messages now carry these - the one-shot popup and a
 * conversation line - and a second copy is how one of them comes to serialize a variant as something
 * else. There is no `else` branch: a new variant must fail to compile here.
 */
internal fun DialogArg.toBnet(): DialogSmsgProto.DialogArg {
  val builder = DialogSmsgProto.DialogArg.newBuilder()

  when (this) {
    is DialogArg.Text -> builder.setText(value)
    is DialogArg.Number -> builder.setNumber(value)
    is DialogArg.Entity -> builder.setEntityId(entityId)
    is DialogArg.Item -> builder.setItemId(itemId)
    is DialogArg.Skill -> builder.setSkillId(skillId)
    is DialogArg.Token -> builder.setToken(key)
    is DialogArg.Name -> builder.setName(value)
  }

  return builder.build()
}
