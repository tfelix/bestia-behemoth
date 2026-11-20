package net.bestia.worldgen.stronghold

/** Station channels on a `FeatureKind.EMPYRITE_SEED` marker. */
object EmpyriteSeedChannels {

  /** Place in the pool, 0 first. Seeds wake in this order. */
  const val RANK = "rank"

  /** Worth of the ore around the seed: the sum of value × richness of every deposit within reach. */
  const val WORTH = "worth"

  /** Seed for `Names.place`, in [CULTURE]. Unique among the seeds and the settlements of the world. */
  const val NAME_SEED = "name_seed"

  /** Culture of the people who live nearest, which names the seed. */
  const val CULTURE = "culture"

  /** Index of the nearest standing settlement, or -1 where nobody lives. Its patron god tells the legend. */
  const val SETTLEMENT = "settlement"
}
