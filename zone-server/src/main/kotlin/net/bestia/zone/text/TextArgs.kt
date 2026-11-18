package net.bestia.zone.text

import net.bestia.bnet.proto.TextProto

/**
 * The one place a [TextArg] becomes wire.
 *
 * There is no `else` branch: a new variant must fail to compile here.
 */
internal fun TextArg.toBnet(): TextProto.TextArg {
  val builder = TextProto.TextArg.newBuilder()

  when (this) {
    is TextArg.Text -> builder.setText(value)
    is TextArg.Number -> builder.setNumber(value)
    is TextArg.Entity -> builder.setEntityId(entityId)
    is TextArg.Item -> builder.setItemId(itemId)
    is TextArg.Skill -> builder.setSkillId(skillId)
    is TextArg.Species -> builder.setSpeciesId(speciesId)
    is TextArg.Token -> builder.setToken(key)
    is TextArg.Name -> builder.setName(value)
  }

  return builder.build()
}
