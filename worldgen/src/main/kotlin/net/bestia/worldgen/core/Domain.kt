package net.bestia.worldgen.core

/**
 * What a god is the god of. Every world draws its gods from this catalogue and names them anew; see the
 * pantheon page in bestia-docs, which this list and its two columns follow.
 */
enum class Domain(
  /** The Order the god's followers find easiest to believe, or null for none. A hint, never a rule. */
  val leaning: Faction?,
  /** The kind of bestia the god's followers honour, or null for none. */
  val sacred: BeastKind?
) {
  WAR(Faction.CHAOS, BeastKind.BEAST),
  LOVE(Faction.CIRCLE, BeastKind.BIRD),
  DEATH(Faction.CIRCLE, BeastKind.UNDEAD),
  SUN(Faction.ETERNITY, BeastKind.BIRD),
  MOON(Faction.CIRCLE, BeastKind.BEAST),
  SKY(Faction.ETERNITY, BeastKind.DRAGON),
  SEA(Faction.CHAOS, BeastKind.AQUATIC),
  HARVEST(Faction.CIRCLE, BeastKind.PLANT),
  HEARTH(Faction.ETERNITY, null),
  WISDOM(Faction.ETERNITY, BeastKind.BIRD),
  CRAFT(Faction.ETERNITY, BeastKind.ELEMENTAL),
  TRADE(null, null),
  TRICKERY(Faction.CHAOS, BeastKind.INSECT),
  HEALING(Faction.ETERNITY, BeastKind.REPTILE),
  HUNT(Faction.CIRCLE, BeastKind.BEAST),
  FATE(Faction.CIRCLE, BeastKind.INSECT),
  JUSTICE(Faction.ETERNITY, null),
  REVELRY(Faction.CHAOS, BeastKind.PLANT),
  NIGHT(Faction.CHAOS, BeastKind.FORMLESS);

  companion object {
    /** By name, so a reorder is free; both columns reach what a world's gods are and what their cults believe. */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (domain in entries) digest.put(domain.name, "${domain.leaning?.name}:${domain.sacred?.name}")
      return digest.value
    }
  }
}
