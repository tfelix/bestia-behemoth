package net.bestia.zone.casting.sense

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.MonsterSenseSmsgProto
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.message.SMSG

data class MonsterSenseSMSG(
  val entityId: Long,
  val level: Int,
  val element: String,
  val kind: String,
  val size: String,
  val hp: Int,
  val maxHp: Int,
  val mana: Int,
  val maxMana: Int,
  val attributes: StatusValues,
  val defense: Int,
  val magicDefense: Int,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val sensed = MonsterSenseSmsgProto.MonsterSenseSMSG.newBuilder()
      .setEntityId(entityId)
      .setLevel(level)
      .setElement(element)
      .setKind(kind)
      .setSize(size)
      .setHp(hp)
      .setMaxHp(maxHp)
      .setMana(mana)
      .setMaxMana(maxMana)
      .setStrength(attributes.strength)
      .setVitality(attributes.vitality)
      .setIntelligence(attributes.intelligence)
      .setAgility(attributes.agility)
      .setWillpower(attributes.willpower)
      .setDexterity(attributes.dexterity)
      .setDefense(defense)
      .setMagicDefense(magicDefense)

    return EnvelopeProto.Envelope.newBuilder().setMonsterSense(sensed).build()
  }
}
