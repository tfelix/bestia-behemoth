package net.bestia.worldgen.resource

import net.bestia.worldgen.core.ParamsDigest
/**
 * What can be dug, felled or panned out of the ground.
 *
 * Ordinals are not persisted - deposits are stored as features with a named attribute - so this list may be
 * reordered freely, unlike [net.bestia.worldgen.bio.Biome] or
 * [net.bestia.worldgen.voxel.BlockType].
 */
enum class ResourceType(val label: String) {

  // Metals. Each has a genuine geological setting, which is the whole point of placing them causally.
  COPPER("copper"),
  TIN("tin"),
  IRON("iron"),
  GOLD_LODE("gold lode"),
  GOLD_PLACER("placer gold"),
  SILVER("silver"),

  /**
   * The one metal with no real-world counterpart, and the only reason to have invented one: every other ore
   * here can be worked by a civilisation that already exists on the map, so none of them is a reason for a
   * *player* to go anywhere. Mithrandium sits deeper than a medieval shaft reaches, in the roots of the
   * oldest mountains, so the world has a resource that is found rather than inherited.
   */
  MITHRANDIUM("mithrandium"),

  /**
   * Ore that came out of corrupted ground.
   *
   * **Deliberately not a [MinableOre], and therefore never placed by [ResourceStage].** There is no aetherite
   * deposit anywhere in any world: a body is iron or copper like any other, and `ChunkStructures.OreVeins`
   * decides at materialisation that the rock around this one is corrupted enough for what comes out of it to
   * be aetherite. That is what avoids a cycle - resources would otherwise need corruption, which needs
   * settlements, which needs resources - and it is also the better story.
   *
   * It exists as a [ResourceType] for exactly one reason: `OreBlocks.yieldOf` has to be able to name what a
   * player just broke. Nothing else reads it, and [value] never reaches the value field, because no marker
   * ever carries `TYPE = AETHERITE`.
   */
  AETHERITE("aetherite"),

  // Bulk minerals.
  SALT("salt"),
  STONE("building stone"),
  CLAY("clay"),
  MARBLE("marble"),

  // Surface resources, biome derived.
  TIMBER("timber"),
  FURS("furs"),
  FISH("fish");

  /** How much a unit of this is worth, roughly. Feeds settlement scoring and later the economy. */
  val value: Double
    get() = when (this) {
      // Above gold, but it is deep enough that no settlement can reach it, so this weight only ever reaches
      // the value field through the handful of shallow outliers - which is the intent.
      // Above mithrandium, and as unreachable as it: nothing places an aetherite marker, so this weight
      // cannot reach RESOURCE_VALUE and cannot move a settlement toward ground no civilisation could work.
      AETHERITE -> 1.5
      MITHRANDIUM -> 1.2
      GOLD_LODE, GOLD_PLACER -> 1.0
      SILVER -> 0.75
      COPPER, TIN -> 0.5
      IRON -> 0.55
      MARBLE -> 0.4
      SALT -> 0.45
      FURS -> 0.3
      TIMBER -> 0.25
      STONE, CLAY -> 0.15
      FISH -> 0.2
    }

  companion object {

    /**
     * Fingerprint of the worth table.
     *
     * [value] feeds settlement scoring and the economy, so these numbers move where cities go. Folded by
     * name; the ordinals are explicitly *not* persisted anywhere (see this enum's own note), so their order is
     * free and only the values matter.
     */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (type in entries) digest.put(type.name, type.value)
      return digest.value
    }
  }
}

/**
 * How rich one voxel of ore is.
 *
 * Three grades rather than one block per ore, because the block is what a player breaks and the grade is what
 * decides what drops out of it. A single ore block would make every swing of a pick worth the same, which
 * removes the only thing that makes one part of an orebody more interesting than another.
 *
 * The yields themselves are *not* here - they are in [ResourceParams.grades], because they are balancing
 * numbers a designer retunes, not facts about the world.
 */
enum class OreGrade {
  SMALL,
  MEDIUM,
  RICH
}

/**
 * The resources a player can actually mine out of the rock, with the numbers that decide how much there is.
 *
 * A subset of [ResourceType]: timber, furs, fish, stone, clay and marble are all resources a *settlement*
 * works, and they have no orebody, no tonnage and no grade blocks. Everything in this list does.
 *
 * [GOLD_PLACER] is deliberately absent. Placer gold is traced downstream of a lode rather than placed, so it
 * has no candidate spacing and no scarcity rank of its own; [ResourceStage] gives it its own small shallow
 * tonnage and it materialises as gold blocks like any other gold.
 *
 * @property spacingFactor how much rarer than the baseline this ore's candidate sites are. Precious metals
 *   are scarce, salt is not. Kept separate from suitability on purpose: suitability answers "could it be
 *   here", scarcity answers "how often".
 * @property scarcityRank who picks their ground first in the dispersal pass. Rarest goes first, so a
 *   mithrandium body is never crowded out by an iron body that had a marginally better score.
 * @property tonsPerThousandSqKm how much of this metal a thousand square kilometres of world holds, in tons.
 *   **An abundance, not a deposit size.** How much is in one deposit is this divided by how many deposits the
 *   world ended up with, so the same number describes a 128 km world and a 4096 km one - see
 *   [ResourceParams.minSitesAcross] and the guaranteed minimum, both of which put *more* deposits on a small
 *   world than its area alone would. Without a density the two would compound and a small world would come out
 *   many times richer per square kilometre than a large one. Calibrated against 512 km worlds, so those hold
 *   what they always held.
 */
enum class MinableOre(
  val resource: ResourceType,
  val scarcityRank: Int,
  val spacingFactor: Double,
  val tonsPerThousandSqKm: Double,
  val minDepth: Double,
  val maxDepth: Double
) {

  /**
   * Its [spacingFactor] only matches gold's, not because it is as common but because it does not need to be
   * scarcer than that: the three-way conjunction of old, hard *and* high ground already thins it further than
   * any of the others, and pushing the spacing out on top of it produced worlds with none in them at all.
   */
  MITHRANDIUM(ResourceType.MITHRANDIUM, 0, 2.4, 0.20, 250.0, 600.0),
  GOLD(ResourceType.GOLD_LODE, 1, 2.4, 0.49, 10.0, 150.0),
  SILVER(ResourceType.SILVER, 2, 2.2, 0.79, 10.0, 150.0),
  TIN(ResourceType.TIN, 3, 1.7, 0.92, 10.0, 150.0),

  /**
   * Ahead of copper despite being commoner, which is the one place this ordering is deliberate rather than
   * merely descriptive. Copper's geology is the arcs and nothing else; iron's overlaps it, so leaving copper
   * to pick first meant it took every arc site and iron got what was left. Iron is also the ore the economy
   * cannot do without, so if one of the two has to be crowded it should not be this one.
   */
  IRON(ResourceType.IRON, 4, 1.4, 2.0, 10.0, 150.0),

  /** Bedded halite, which is why it is shallow: it is the floor of a lake that dried up, not a vein. */
  SALT(ResourceType.SALT, 5, 1.2, 1.05, 0.0, 6.0),

  COPPER(ResourceType.COPPER, 6, 1.2, 6.6, 10.0, 150.0);

  init {
    require(spacingFactor > 0.0) { "$name spacingFactor must be positive, was $spacingFactor" }
    require(tonsPerThousandSqKm > 0.0) {
      "$name tonsPerThousandSqKm must be positive, was $tonsPerThousandSqKm"
    }
    require(minDepth >= 0.0) { "$name minDepth must not be negative, was $minDepth" }
    require(maxDepth >= minDepth) { "$name maxDepth $maxDepth is below minDepth $minDepth" }
  }

  /** Tons of this metal a world of [areaSqMetres] holds in total, across however many deposits it got. */
  fun worldTons(areaSqMetres: Double) =
    tonsPerThousandSqKm * (areaSqMetres / SQ_METRES_PER_THOUSAND_SQ_KM)

  /** Metres below the surface for a deposit whose depth roll came out at [roll]. */
  fun depthAt(roll: Double) = minDepth + (maxDepth - minDepth) * roll.coerceIn(0.0, 1.0)

  companion object {

    private val BY_RESOURCE = entries.associateBy { it.resource }

    /** The mining facts for a resource, or null if it is not something in the rock. */
    fun of(resource: ResourceType): MinableOre? = BY_RESOURCE[resource]

    /** Ordered rarest first, which is the order the dispersal pass hands out ground in. */
    val byScarcity: List<MinableOre> = entries.sortedBy { it.scarcityRank }

    /** A thousand square kilometres, in square metres - the unit [tonsPerThousandSqKm] is quoted in. */
    private const val SQ_METRES_PER_THOUSAND_SQ_KM = 1_000_000_000.0

    /**
     * Fingerprint of the mining table.
     *
     * Every number here moves how much metal a world contains and where, so it belongs in the stage's
     * params version even though none of it is a [net.bestia.worldgen.core.Params] field.
     */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (ore in entries) {
        digest
          .put("${ore.name}.scarcityRank", ore.scarcityRank.toDouble())
          .put("${ore.name}.spacingFactor", ore.spacingFactor)
          .put("${ore.name}.tonsPerThousandSqKm", ore.tonsPerThousandSqKm)
          .put("${ore.name}.minDepth", ore.minDepth)
          .put("${ore.name}.maxDepth", ore.maxDepth)
      }
      return digest.value
    }
  }
}

/** Station channel names on an [net.bestia.worldgen.vector.FeatureKind.ORE_DEPOSIT] marker. */
object DepositChannels {

  /** [ResourceType] ordinal. A category, so it is read back with `toInt()` and never interpolated. */
  const val TYPE = "resource_type"

  /**
   * Extractable material in **metric tons**.
   *
   * For anything in [MinableOre] this is a real number that reconciles with the voxels: [OreBody] derives
   * [RADIUS] from it, and the sum of what every ore voxel in the body drops comes back to it. It is tonnage
   * *in place* and therefore an upper bound - ore stops below the soil, and a cave or a cellar cut through
   * the body takes its share with it.
   *
   * For the surface resources it is a nominal magnitude on the same scale, because a forest has no orebody
   * and nothing downstream reads the number for them.
   */
  const val TONS = "tons"

  /** Concentration in `[0,1]`. Decides yield per unit of work, and whether it is worth a mine at all. */
  const val RICHNESS = "richness"

  /** Metres below the surface. Zero for a surface outcrop, which is what gets discovered first. */
  const val DEPTH = "depth"

  /**
   * Horizontal extent of the orebody in metres.
   *
   * Needed by chunk generation and by nothing else: a deposit is a point as far as the world tier is
   * concerned, but a voxel has to know whether it is inside the body or outside it.
   */
  const val RADIUS = "radius"
}
