package net.bestia.model.bestia

import com.fasterxml.jackson.annotation.JsonProperty

interface StatusValues {
  val physicalDefense: Int
  val magicDefense: Int
  val strength: Int
  val vitality: Int
  val intelligence: Int
  val agility: Int
  val willpower: Int
  val dexterity: Int
}