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

  /**
   * A gemstone with no real-world counterpart, grown in the gas cavities of a cooled lava flow.
   *
   * The world's **first gem**, and that is the argument for inventing it rather than adding one of the six real
   * volcanic minerals that were considered beside it. Every one of those - cinnabar, realgar, sal ammoniac,
   * tephra, pumice - earns its place only through a refining or crafting system, and there is none:
   * `grep ResourceType zone-server/src/main` returns nothing, and `OreBlocks.yieldOf`'s own KDoc says the thing
   * that lets a player break an ore voxel and pick up the metal does not exist yet. A gem needs no such system
   * to be worth walking to.
   *
   * It is the second invented material after [MITHRANDIUM] and the second reason to go somewhere specific, which
   * is the case that KDoc makes for having invented one at all. The `-lith` suffix is a third category marker
   * beside mithrandium's `-ium` for a deep metal and aetherite's `-ite` for a corrupted one, so what kind of
   * thing it is reads off the name.
   *
   * Keyed on volcanism **alone** - no mana term - so `ResourceStage` gains no dependency on `ManaStage` and the
   * gem is found wherever a strong volcanic field is rather than wherever two rare things coincide.
   */
  PYRELITH("pyrelith"),

  /*
   * The real gems, added after pyrelith proved the shape worked.
   *
   * Pyrelith's KDoc argues for inventing a material because an invented one is something a player has to come
   * here to learn. These are the opposite argument and both are worth having: a player already knows what a
   * diamond is worth, so a diamond needs no explaining and no crafting system to be a reason to dig. What each
   * one *does* have to earn is a geological setting nothing else occupies - see `ResourceStage.suitabilityFor`,
   * where all four are one arm each and no two share a conjunction of terms.
   */

  /**
   * Corundum in marble, at a collision belt.
   *
   * Shares [MARBLE]'s suitability terms deliberately: ruby genuinely is a marble-hosted gem, so the ground
   * that makes one makes the other, and pretending otherwise would be inventing geology to avoid a
   * coincidence. Depth is what separates them - marble is quarried off the surface, ruby is dug for - which is
   * the same separation `MinableOre.PYRELITH` and `SULFUR` already rely on.
   */
  RUBY("ruby"),

  /**
   * Kimberlite through the keel of an old craton: the deepest thing in the world worth digging for.
   *
   * The only entry in this enum that keys on being **away** from a plate boundary, and that is what keeps it
   * off every other precious deposit's ground - copper, gold, silver and ruby all want the arc. Real
   * kimberlite erupts through crust that has been quiet for an age and never at a subduction zone, so the
   * anti-correlation is the geology rather than a spacing trick.
   */
  DIAMOND("diamond"),

  /** Beryl in a granite pegmatite: `TIN`'s plutons, several times deeper and a great deal rarer. */
  EMERALD("emerald"),

  /**
   * Quartz geodes in soft, vuggy cover.
   *
   * Deliberately the cheap one, and it is the only entry here placed for an economic reason rather than a
   * geological one. Every other gem is rare, deep and worth an expedition, which left a player digging their
   * first shaft with nothing at all to find; this is two to forty-five metres down, an order of magnitude more
   * abundant than the rest, and worth about what salt is. A gem economy needs a floor as much as a ceiling.
   */
  AMETHYST("amethyst"),

  // Bulk minerals.
  SALT("salt"),
  STONE("building stone"),
  CLAY("clay"),
  MARBLE("marble"),

  /**
   * Sulfur, from the crusts and sublimates around a vent.
   *
   * Earns its place today without any crafting system: it raises `RESOURCE_VALUE` on the arc, and
   * `SpecialSites.mines` reads any `ORE_DEPOSIT` marker - so a sulfur mine and its `MINE_OPENED` chronicle line
   * come for free with no edit to history at all.
   */
  SULFUR("sulfur"),

  /**
   * Volcanic glass, from the chilled margin of a flow.
   *
   * A [PLAIN] resource rather than a graded ore, and that is the geology rather than a simplification: obsidian
   * is not disseminated through rock in veins you assay, it is a massive glassy carapace you quarry - which is
   * `MARBLE`'s shape exactly. It is also the one entry here that reads as volcanic from a distance.
   */
  OBSIDIAN("obsidian"),

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
      // Above gold and below mithrandium, and as good as unreachable either way: at 300-800 m a diamond pipe
      // is deeper than anything a settlement can work, so like mithrandium this weight only ever reaches the
      // value field through a shallow outlier. That is the intent rather than a limitation.
      DIAMOND -> 1.1
      GOLD_LODE, GOLD_PLACER -> 1.0
      // Ruby sits on the arc like copper, gold and silver do, and it is worth more than any of them - so it is
      // the strongest new pull toward volcanic and orogenic ground that `Terms.hazard`'s volcanic term has to
      // absorb. See PYRELITH below for why that term exists at all.
      RUBY -> 0.9
      // Between silver and gold. High enough to be worth the walk, and the reason `Terms.hazard` needed a
      // volcanic term in the same commit: without one, a valuable ore on the arc pulls settlements *onto* the
      // volcanoes through RESOURCE_VALUE, and then eruptions fire far more often than their rate implies.
      PYRELITH -> 0.85
      EMERALD -> 0.8
      SILVER -> 0.75
      // Priced with salt, not with the gems. The whole point of it is to be the one a player finds early.
      AMETHYST -> 0.35
      COPPER, TIN -> 0.5
      IRON -> 0.55
      SULFUR -> 0.45
      MARBLE -> 0.4
      SALT -> 0.45
      FURS -> 0.3
      OBSIDIAN -> 0.3
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
  val maxDepth: Double,

  /**
   * Whether `ResourceStage.guarantee` puts a floor of deposits on a world that placed none by score.
   *
   * True for everything whose geology every world has. The guarantee is justified there by the argument at
   * `ResourceStage`'s own floor - "the geology is already there, the sampler simply missed them" - and that is
   * a true statement about copper on a world with arcs and about mithrandium on a world with old crust.
   *
   * It is **false** about volcanism, and that is the whole reason this flag exists. A seed can legitimately have
   * no convergent boundary and no hotspot on land - `ResourceStage` and `HistoryStage` both already handle it -
   * and on such a world the floor would put three pyrelith mines on the three least-bad cells in a world with no
   * volcano in it. A resource that is *absent* from some worlds is a stronger fact about the map than one that
   * is merely rare on all of them.
   *
   * ### The gems, and what the floor turned out to cost
   *
   * It is false about the rare gems too, for a related reason and one measured rather than argued. A floor is
   * not free: `guarantee` tops up from the best cells the world has and then blocks everything within
   * `oreSeparation` of each, so **every guaranteed ore spends some of a small world's ground**. Adding four
   * gems at three deposits each took a 128 km world from comfortable to over-subscribed, and what gave way was
   * `MITHRANDIUM` - down to one deposit from three, because diamond wants old crust too and picks first. A
   * floor that breaks another ore's floor is not a floor.
   *
   * So the three expensive gems opt out. Their preconditions do exist on every world, unlike volcanism, but a
   * guarantee is a promise about the *economy* - iron and copper have to be there or a civilisation cannot be
   * built - and a diamond is a luxury. "This world has no diamond mine" is a fact worth having; "this world has
   * no iron" is a broken world. [AMETHYST] keeps its guarantee and is the reason the others can drop theirs:
   * every world still has a gem in it, and the one it is guaranteed is the cheap one a player finds early.
   */
  val guaranteed: Boolean = true
) {

  /**
   * The deepest and rarest thing in the world, and **first in the dispersal order** for the same reason
   * pyrelith is near the front: its ground is the scarcest here. A craton keel is old crust that is also flat
   * and also nowhere near a plate boundary, and the three together are a small fraction of a small fraction.
   *
   * Deep enough that no settlement works it - that is deliberate, and it is [ResourceType.DIAMOND]'s whole
   * point. Nothing else in this table starts below 300 m.
   *
   * Its [spacingFactor] matches [MITHRANDIUM]'s rather than exceeding it, and that is [MITHRANDIUM]'s own
   * argument applied to a tighter conjunction: old crust *and* worn flat *and* away from a boundary already
   * thins this further than any of the others, so widening the candidate spacing on top of it buys nothing and
   * costs worlds with no diamond in them. It was 2.8 for one measurement, which is also what turned
   * `ResourceParams.spacingShrink` from a no-op into an active floor on a 384 km world - the rarest ore's
   * spacing is what that threshold is computed from, so this number has reach well beyond diamond.
   */
  DIAMOND(ResourceType.DIAMOND, 0, 1.9, 0.03, 300.0, 800.0, guaranteed = false),

  /**
   * Its [spacingFactor] only matches gold's, not because it is as common but because it does not need to be
   * scarcer than that: the three-way conjunction of old, hard *and* high ground already thins it further than
   * any of the others, and pushing the spacing out on top of it produced worlds with none in them at all.
   */
  MITHRANDIUM(ResourceType.MITHRANDIUM, 1, 2.4, 0.20, 250.0, 600.0),

  /**
   * Ahead of gold and silver on ground it shares with both, which is `IRON`'s argument applied to a gem: ruby
   * wants the same arc the precious metals want, and there are far more arc cells that suit gold than suit
   * ruby, so letting gold pick first would cost ruby more than it costs gold.
   */
  RUBY(ResourceType.RUBY, 2, 2.4, 0.09, 60.0, 220.0, guaranteed = false),

  /**
   * Pegmatite in `TIN`'s plutons, and ahead of tin for ruby's reason: the rarer of two sharing ground picks
   * first.
   *
   * Its floor is 120 m and its ceiling stops at 240, one metre-band short of [MITHRANDIUM]'s 250, and that
   * ceiling is not a rounding of anything. It was 400 first, which put emerald *through* the depth at which
   * mithrandium starts - and the world's one durable claim about depth is that there is a tier below what a
   * medieval shaft reaches, holding mithrandium and diamond and nothing else. A gem that straddles the
   * boundary erases the tier. `OreDepositTest.the deep ores lie below everything a shaft reaches` is the
   * tripwire, and it caught exactly this.
   */
  EMERALD(ResourceType.EMERALD, 3, 2.4, 0.11, 120.0, 240.0, guaranteed = false),

  GOLD(ResourceType.GOLD_LODE, 4, 2.4, 0.49, 10.0, 150.0),

  /**
   * Vugs in the interior of a thick flow, so shallow to mid depth and scarcer than silver.
   *
   * Above silver in the dispersal order, because its ground is the scarcest of any *volcanic* deposit - a
   * strong volcanic field is about a tenth of the volcanic country, which is about a hundredth of the land.
   * Letting the metals pick first would mean pyrelith took whatever was left of an already tiny candidate set.
   */
  PYRELITH(ResourceType.PYRELITH, 5, 2.4, 0.16, 5.0, 90.0, guaranteed = false),

  SILVER(ResourceType.SILVER, 6, 2.2, 0.79, 10.0, 150.0),
  TIN(ResourceType.TIN, 7, 1.7, 0.92, 10.0, 150.0),

  /**
   * Ahead of copper despite being commoner, which is the one place this ordering is deliberate rather than
   * merely descriptive. Copper's geology is the arcs and nothing else; iron's overlaps it, so leaving copper
   * to pick first meant it took every arc site and iron got what was left. Iron is also the ore the economy
   * cannot do without, so if one of the two has to be crowded it should not be this one.
   */
  IRON(ResourceType.IRON, 8, 1.4, 2.0, 10.0, 150.0),

  /** Bedded halite, which is why it is shallow: it is the floor of a lake that dried up, not a vein. */
  SALT(ResourceType.SALT, 9, 1.2, 1.05, 0.0, 6.0),

  /**
   * The one gem down here among the bulk minerals, and it belongs here rather than with the others.
   *
   * Geodes form in soft cover, which is the commonest ground in this whole table and the one thing no other
   * deposit competes for - so it picks late and loses nothing by it. Shallow, abundant and cheap on purpose:
   * see [ResourceType.AMETHYST].
   */
  AMETHYST(ResourceType.AMETHYST, 10, 1.4, 1.2, 2.0, 45.0),

  COPPER(ResourceType.COPPER, 11, 1.2, 6.6, 10.0, 150.0),

  /**
   * Sulfur crusts, in `SALT`'s shape: shallow, bedded, and hand-worked rather than mined out of a vein.
   *
   * **Last in the dispersal order**, below copper, and deliberately so even though its ground is far rarer than
   * copper's. Its own volcanism gate already thins it to the arcs, so it needs no priority - and letting it pick
   * before the metals would mean sulfur taking arc sites that copper and iron are competing for on the same
   * ground. The economy can do without sulfur and cannot do without iron, which is `IRON`'s own argument one
   * place further down the list.
   */
  SULFUR(ResourceType.SULFUR, 12, 1.3, 1.4, 0.0, 12.0, guaranteed = false);

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
          .put("${ore.name}.guaranteed", if (ore.guaranteed) 1.0 else 0.0)
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
