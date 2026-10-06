package net.bestia.zone.account.master.status

import net.bestia.zone.account.master.Master
import net.bestia.zone.battle.ecs.status.BaseStatusValues

/**
 * Attribute-keyed access to a [Master]'s six persisted effort values, so callers that already work in
 * terms of a [StatusAttribute] don't each need their own `when` over the six columns.
 */
fun Master.effortValue(attribute: StatusAttribute): Int = when (attribute) {
  StatusAttribute.STRENGTH -> strength
  StatusAttribute.AGILITY -> agility
  StatusAttribute.VITALITY -> vitality
  StatusAttribute.INTELLIGENCE -> intelligence
  StatusAttribute.DEXTERITY -> dexterity
  StatusAttribute.WILLPOWER -> willpower
}

fun Master.setEffortValue(attribute: StatusAttribute, value: Int) {
  when (attribute) {
    StatusAttribute.STRENGTH -> strength = value
    StatusAttribute.AGILITY -> agility = value
    StatusAttribute.VITALITY -> vitality = value
    StatusAttribute.INTELLIGENCE -> intelligence = value
    StatusAttribute.DEXTERITY -> dexterity = value
    StatusAttribute.WILLPOWER -> willpower = value
  }
}

/** All six effort values as a map, in [StatusAttribute] declaration order. */
fun Master.effortValues(): Map<StatusAttribute, Int> =
  StatusAttribute.entries.associateWith { effortValue(it) }

/** The live counterpart of [Master.effortValue]: the persister writes these six back to the master's columns. */
fun BaseStatusValues.effortValue(attribute: StatusAttribute): Int {
  return when (attribute) {
    StatusAttribute.STRENGTH -> strength
    StatusAttribute.AGILITY -> agility
    StatusAttribute.VITALITY -> vitality
    StatusAttribute.INTELLIGENCE -> intelligence
    StatusAttribute.DEXTERITY -> dexterity
    StatusAttribute.WILLPOWER -> willpower
  }
}

fun BaseStatusValues.raiseEffortValue(attribute: StatusAttribute, amount: Int) {
  when (attribute) {
    StatusAttribute.STRENGTH -> strength += amount
    StatusAttribute.AGILITY -> agility += amount
    StatusAttribute.VITALITY -> vitality += amount
    StatusAttribute.INTELLIGENCE -> intelligence += amount
    StatusAttribute.DEXTERITY -> dexterity += amount
    StatusAttribute.WILLPOWER -> willpower += amount
  }
}
