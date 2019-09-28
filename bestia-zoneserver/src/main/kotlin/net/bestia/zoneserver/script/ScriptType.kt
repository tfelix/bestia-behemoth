package net.bestia.zoneserver.script

enum class ScriptType {
  ITEM, // Is executed upon usage of this item.
  CONDITION, // Alters the Bestias condition like Buffs, Debuffs, Euipment Effects etc.
  ATTACK
}