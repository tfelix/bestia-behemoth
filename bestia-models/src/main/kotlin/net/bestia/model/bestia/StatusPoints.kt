package net.bestia.model.bestia

import com.fasterxml.jackson.annotation.JsonProperty

interface StatusPoints {
  @get:JsonProperty("def")
  var defense: Int

  @get:JsonProperty("mdef")
  var magicDefense: Int

  @get:JsonProperty("str")
  var strength: Int

  @get:JsonProperty("vit")
  var vitality: Int

  @get:JsonProperty("int")
  var intelligence: Int

  @get:JsonProperty("agi")
  var agility: Int

  @get:JsonProperty("will")
  var willpower: Int

  @get:JsonProperty("dex")
  var dexterity: Int

  /**
   * Copies all values from the given [StatusPoints] instance.
   *
   * @param rhs The [StatusPoints] object to copy.
   */
  fun set(rhs: StatusPoints)
}