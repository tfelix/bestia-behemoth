package net.bestia.worldgen.core

/**
 * The five world-boss species. A world's history wakes some of them; none spawns in a normal den.
 * [identifier] is the mob identifier the zone server spawns, so a test there checks each exists as a world boss.
 */
enum class WorldBoss(
  val identifier: String,
  /** How the chronicle calls the species: "Gharzul, the Bone Wyrm". */
  val title: String,
  val level: Int,
  val lair: LairKind,
  /** The kinds of past that can wake this one, most fitting first. */
  val origins: List<MonsterOrigin>
) {
  OSSUARY_WYRM(
    "ossuary-wyrm", "the Bone Wyrm", 108, LairKind.CAVE,
    listOf(MonsterOrigin.ROBBED_TOMB, MonsterOrigin.RISEN_DEAD)
  ),
  LEVIATHAN(
    "leviathan", "the Leviathan", 112, LairKind.OCEAN,
    listOf(MonsterOrigin.WOUND, MonsterOrigin.SUMMONED)
  ),
  BEHEMOTH(
    "behemoth", "the Behemoth", 120, LairKind.REMOTE_LAND,
    listOf(MonsterOrigin.ERUPTION, MonsterOrigin.WOUND)
  ),
  EMBER_SOVEREIGN(
    "ember-sovereign", "the Ember Sovereign", 110, LairKind.CAVE,
    listOf(MonsterOrigin.ERUPTION, MonsterOrigin.SUMMONED)
  ),
  MIRE_MOTHER(
    "mire-mother", "the Mire Mother", 104, LairKind.REMOTE_LAND,
    listOf(MonsterOrigin.RISEN_DEAD, MonsterOrigin.SUMMONED, MonsterOrigin.WOUND)
  );

  companion object {
    /** By name: identifiers, titles, levels and lairs all reach a world's history. */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (boss in entries) {
        digest.put(boss.name, "${boss.identifier}:${boss.title}:${boss.level}:${boss.lair}:${boss.origins}")
      }
      return digest.value
    }
  }
}
