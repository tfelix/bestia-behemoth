package net.bestia.model.bestia

import com.fasterxml.jackson.annotation.JsonProperty

interface StatusValues {
  @get:JsonProperty("def")
  val physicalDefense: Int

  @get:JsonProperty("mdef")
  val magicDefense: Int

  @get:JsonProperty("str")
  val strength: Int

  @get:JsonProperty("vit")
  val vitality: Int

  @get:JsonProperty("int")
  val intelligence: Int

  @get:JsonProperty("agi")
  val agility: Int

  @get:JsonProperty("will")
  val willpower: Int

  @get:JsonProperty("dex")
  val dexterity: Int
}