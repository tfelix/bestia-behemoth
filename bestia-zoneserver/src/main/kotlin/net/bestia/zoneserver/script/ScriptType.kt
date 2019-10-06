package net.bestia.zoneserver.script

enum class ScriptType {
  BASIC, // Script is located in the root folder
  ITEM, // Is executed upon usage of this item.
  CONDITION, // Alters the Bestias condition like Buffs, Debuffs, Euipment Effects etc.
  ATTACK
}