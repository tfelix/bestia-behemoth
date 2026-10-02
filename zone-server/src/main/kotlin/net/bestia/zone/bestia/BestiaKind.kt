package net.bestia.zone.bestia

/**
 * What a species is: what it is made of and how it lives. Two bestias only breed when they share a kind, see
 * https://docs.bestia-game.net/docs/mechanics/bestia/#breeding.
 *
 * The design docs keep the same list in `data/bestia_blueprint.yaml`, where the Bestia Blueprint Calculator
 * offers it. A new kind goes into both.
 */
enum class BestiaKind {
  BEAST,
  BIRD,
  REPTILE,
  AQUATIC,
  INSECT,
  PLANT,
  FORMLESS,
  ELEMENTAL,
  HUMANOID,
  UNDEAD,
  DEMON,
  DRAGON
}
