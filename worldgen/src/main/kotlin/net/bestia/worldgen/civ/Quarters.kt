package net.bestia.worldgen.civ

import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs
import kotlin.math.atan2

/**
 * How closely built and how regularly a quarter is laid out.
 *
 * ### The four numbers that make quarters look different
 *
 * A quarter's *function* is not what a player sees from a rooftop - the buildings all look much alike from there.
 * What is visible is the **grain**: how big the plots are, how regular the rows are, and how much of the block is
 * yard rather than house. A patrician quarter of large regular plots with gardens and a slum of small crooked
 * ones packed to the edges read as different places before anyone reads a sign, and they differ in these four
 * numbers and nothing else.
 *
 * This is the part of the reference generator's design worth taking almost literally, because it is the part that
 * is hard-won tuning rather than algorithm: one table, four columns, and the whole visual variety of a town.
 *
 * @property minLotArea square metres below which the block subdivider stops cutting. Sets the plot size.
 * @property gridChaos how far a cut may turn from square, in `[0,1]`. Zero is a surveyed grid; high is organic.
 * @property sizeChaos how unequal a cut may be, in `[0,1]`. Zero halves every piece; high gives mixed sizes.
 * @property emptyProb chance a finished plot is left as yard rather than built on.
 */
internal class QuarterGrain(
  val minLotArea: Double,
  val gridChaos: Double,
  val sizeChaos: Double,
  val emptyProb: Double
)

/**
 * Decides what each patch of a town's core is for.
 *
 * ### Designed, not inferred
 *
 * `Districts` grows a quarter *after* the fact, by clustering buildings that turned out to share a trade and
 * taking their convex hull. That was the only thing available without blocks, and its KDoc is honest about the
 * cost: the hull of an L-shaped group claims the corner between the arms, and two interleaving quarters overlap.
 * With a partition in hand a quarter can be *chosen* first - a patch, with an edge, that a street runs along -
 * and the buildings then follow from it. The inferred path stays for the settlements that have no patches.
 *
 * ### Scored, not shuffled
 *
 * Each kind states where it wants to be and the best-scoring free patch is given to it, in an order that puts the
 * choosy kinds first. That ordering is the whole content of the algorithm: the market has to be central, the
 * tanners have to be downwind, and a slum is defined by being where nothing else wanted to go - so residential
 * is not scored at all, it is what is left, which is also what a town is mostly made of.
 */
internal object Quarters {

  /**
   * Where each patch's quarter, in patch order.
   *
   * @param walled whether history gave this settlement a wall, which is what makes a citadel worth siting.
   * @param downwind unit direction the wind blows towards; the noxious trades go this way.
   * @param downstream unit direction surface water leaves; tanning and dyeing go this way too.
   */
  fun assign(
    patches: List<TownPatch>,
    frame: TownFrame,
    tier: SettlementTier,
    walled: Boolean,
    downwind: Vec2d,
    downstream: Vec2d,
    roll: (Long, Long) -> Double
  ): List<DistrictKind> {
    if (patches.isEmpty()) return emptyList()

    val out = arrayOfNulls<DistrictKind>(patches.size)

    // Normalised distance from the town centre, so every score below is in the same units whatever the size of
    // the settlement. Measured to the site rather than the centroid for the reason `TownPatch.site` documents.
    val furthest = patches.maxOf { it.site.distanceTo(frame.centre) }.coerceAtLeast(1.0)
    val fromCentre = patches.map { it.site.distanceTo(frame.centre) / furthest }

    // The market first and unconditionally: it is the one quarter whose position the rest are scored against.
    val market = patches.indices.minBy { fromCentre[it] }
    out[market] = DistrictKind.MARKET

    // A citadel wants defensible ground on the edge and a compact site to fortify. Only where there is a wall for
    // it to belong to, and only on a settlement big enough that a lord would sit in it.
    if (walled && tier <= SettlementTier.TOWN) {
      patches.indices
        .filter { out[it] == null && patches[it].onOutline && patches[it].compactness >= CITADEL_COMPACTNESS }
        .maxByOrNull { fromCentre[it] }
        ?.let { out[it] = DistrictKind.CITADEL }
    }

    // The quarters at the gates, one per road that arrives. A gate quarter is where a town's strangers arrive and
    // is the reason "Egggate" is a place name: it is named for the way in, not for what is made there.
    for ((i, approach) in frame.approaches.withIndex()) {
      if (i >= MAX_GATE_QUARTERS) break
      patches.indices
        .filter { out[it] == null && patches[it].onOutline }
        .minByOrNull { angleBetween((patches[it].site - frame.centre).normalized(), approach) }
        ?.let { out[it] = DistrictKind.GATE }
    }

    // Then the scored kinds, in order of how choosy they are. Each takes the best free patch, or none if the town
    // has no patch it would accept - which is how a village ends up with a craft quarter and no park.
    for (wanted in SCORED) {
      if (wanted.count(patches.size) <= 0) continue

      repeat(wanted.count(patches.size)) {
        val best = patches.indices
          .filter { out[it] == null }
          .maxByOrNull {
            wanted.score(patches[it], fromCentre[it], frame, downwind, downstream) +
                // A little noise, so two patches that score alike do not always resolve the same way and every
                // town of a size does not lay its quarters out in the same order.
                (roll(it.toLong(), SCORE_SALT) - 0.5) * SCORE_JITTER
          }
          ?: return@repeat
        out[best] = wanted.kind
      }
    }

    // Housing is what a town is mostly made of, so it is the default rather than a competitor.
    return out.map { it ?: DistrictKind.RESIDENTIAL }
  }

  /**
   * The grain each quarter is laid out with.
   *
   * [TownLayout] enters here and nowhere else, as a bias on [QuarterGrain.gridChaos]. That is the resolution of
   * what its own KDoc warns about - *"a blend of the two is not a third kind of town, it is a bug"* - because the
   * two are no longer two algorithms to blend. A planned town and a grown one run the same subdivision and differ
   * in how square its cuts are, which is what actually distinguishes a chartered grid from a market town on the
   * ground. A planned town's slums are still crooked and a grown town's cathedral close is still square; the bias
   * shifts both without flattening either.
   */
  fun grainOf(kind: DistrictKind, layout: TownLayout): QuarterGrain {
    val base = BASE_GRAIN.getValue(kind)
    val bias = if (layout == TownLayout.GRID) GRID_CHAOS_BIAS else ORGANIC_CHAOS_BIAS
    return QuarterGrain(
      minLotArea = base.minLotArea,
      gridChaos = (base.gridChaos * bias).coerceIn(0.0, 1.0),
      sizeChaos = base.sizeChaos,
      emptyProb = base.emptyProb
    )
  }

  /**
   * Plot size, regularity and openness per quarter.
   *
   * The market and the parks are the two that are *not* subdivided into plots at all - a market square is the
   * open space the town is arranged around, and a park is the space it kept. They are given a large minimum and a
   * high empty probability rather than a special case in the subdivider, which keeps the one code path.
   */
  private val BASE_GRAIN: Map<DistrictKind, QuarterGrain> = mapOf(
    // An open middle with stalls and shops crowded round it. Finer-grained and less empty than a park, because a
    // market square is a *busy* open space - at a park's grain it came out with four buildings in it and fell under
    // the floor for a district at all, which left a town's most notable quarter off its own gazetteer.
    DistrictKind.MARKET to QuarterGrain(minLotArea = 520.0, gridChaos = 0.4, sizeChaos = 0.5, emptyProb = 0.55),

    // Workshops with yards behind them: middling plots, irregular, and the yard is where the work happens.
    DistrictKind.CRAFT to QuarterGrain(minLotArea = 260.0, gridChaos = 0.55, sizeChaos = 0.45, emptyProb = 0.10),

    // A broad front onto a straight street is the whole point of a civic building.
    DistrictKind.CIVIC to QuarterGrain(minLotArea = 620.0, gridChaos = 0.18, sizeChaos = 0.3, emptyProb = 0.14),

    // The reference case. Ordinary plots, ordinary yards.
    DistrictKind.RESIDENTIAL to QuarterGrain(minLotArea = 220.0, gridChaos = 0.45, sizeChaos = 0.4, emptyProb = 0.07),

    // Small, crooked, and packed to the edges. The lowest minimum in the table and the highest chaos.
    DistrictKind.SLUM to QuarterGrain(minLotArea = 105.0, gridChaos = 0.85, sizeChaos = 0.7, emptyProb = 0.04),

    // Large regular plots with gardens, which is what wealth looks like from above.
    DistrictKind.PATRICIATE to QuarterGrain(minLotArea = 700.0, gridChaos = 0.16, sizeChaos = 0.25, emptyProb = 0.22),

    // Barracks and a yard to drill in: the most regular thing in a medieval town.
    DistrictKind.MILITARY to QuarterGrain(minLotArea = 540.0, gridChaos = 0.10, sizeChaos = 0.2, emptyProb = 0.30),

    // Mostly not built on, which is what makes it a park.
    DistrictKind.PARK to QuarterGrain(minLotArea = 1_100.0, gridChaos = 0.6, sizeChaos = 0.5, emptyProb = 0.80),

    // An inn, a smithy and a customs house crowded around the way in.
    DistrictKind.GATE to QuarterGrain(minLotArea = 240.0, gridChaos = 0.6, sizeChaos = 0.5, emptyProb = 0.08),

    // A keep and its bailey. Almost all of it is the yard the keep stands in.
    DistrictKind.CITADEL to QuarterGrain(minLotArea = 1_400.0, gridChaos = 0.12, sizeChaos = 0.2, emptyProb = 0.55),

    // Farmsteads, if a patch on the edge ever gets one. Large plots, mostly field.
    DistrictKind.FARMLAND to QuarterGrain(minLotArea = 900.0, gridChaos = 0.7, sizeChaos = 0.6, emptyProb = 0.55)
  )

  /** One scored quarter kind: how many a town gets, and where it wants to be. */
  private class Wanted(
    val kind: DistrictKind,
    val count: (patches: Int) -> Int,
    val score: (
      patch: TownPatch,
      fromCentre: Double,
      frame: TownFrame,
      downwind: Vec2d,
      downstream: Vec2d
    ) -> Double
  )

  /**
   * The scored kinds, in the order they choose.
   *
   * Order is precedence: whoever picks first gets the patch it most wants. Civic before patrician before craft
   * before slum, because that is the order of how badly each needs a *particular* place - a cathedral close on
   * the wrong side of town is wrong in a way a slum in the wrong place is not, since a slum's only requirement
   * is that nothing better wanted the ground.
   */
  private val SCORED: List<Wanted> = listOf(
    // Beside the market, which is where every guildhall and cathedral in Europe is.
    Wanted(DistrictKind.CIVIC, { if (it >= 8) 1 else 0 }) { _, fromCentre, _, _, _ -> 1.0 - fromCentre },

    // Near the middle, on a compact site, and *not* downwind of the tanners.
    Wanted(DistrictKind.PATRICIATE, { if (it >= 12) 1 else 0 }) { patch, fromCentre, frame, downwind, _ ->
      val direction = (patch.site - frame.centre).normalized()
      (1.0 - fromCentre) * 0.6 + patch.compactness * 0.3 + (angleBetween(direction, downwind) / Math.PI) * 0.3
    },

    // Downwind or downstream, and out of the middle. Both, because smoke and effluent are different nuisances
    // that a town usually has to put in different places - the same argument `Zoning.inNoxiousDistrict` makes.
    Wanted(DistrictKind.CRAFT, { maxOf(1, it / 7) }) { patch, fromCentre, frame, downwind, downstream ->
      val direction = (patch.site - frame.centre).normalized()
      val nuisance = minOf(angleBetween(direction, downwind), angleBetween(direction, downstream))
      (1.0 - nuisance / Math.PI) * 0.7 + fromCentre * 0.4
    },

    // Barracks want to be near the edge they defend, and on ground you can form up on.
    Wanted(DistrictKind.MILITARY, { if (it >= 16) 1 else 0 }) { patch, fromCentre, _, _, _ ->
      fromCentre * 0.5 + patch.compactness * 0.5 + if (patch.onOutline) 0.3 else 0.0
    },

    // A green that survived being built on, which happens where the ground is awkward - so the least compact
    // patch that nothing else claimed, rather than a chosen beauty spot.
    Wanted(DistrictKind.PARK, { if (it >= 14) 1 else 0 }) { patch, fromCentre, _, _, _ ->
      (1.0 - patch.compactness) * 0.6 + (1.0 - abs(fromCentre - 0.5) * 2.0) * 0.4
    },

    // Where nothing else wanted to be: the edge, the awkward shapes, and downwind of everything.
    Wanted(DistrictKind.SLUM, { maxOf(1, it / 9) }) { patch, fromCentre, _, _, _ ->
      fromCentre * 0.7 + (1.0 - patch.compactness) * 0.4 + if (patch.onOutline) 0.2 else 0.0
    }
  )

  private fun angleBetween(a: Vec2d, b: Vec2d): Double = abs(atan2(a cross b, a dot b))

  /**
   * How compact a patch must be to hold a citadel.
   *
   * The reference generator's number, and the reason to keep it is that it is a threshold on a scale-free measure:
   * a keep and its bailey need a site that is roughly as wide as it is long, whatever the size of the town.
   */
  private const val CITADEL_COMPACTNESS = 0.75

  /** Most gate quarters a town gets, however many roads arrive. Beyond this a town is all gates. */
  private const val MAX_GATE_QUARTERS = 4

  /** Range of the tie-breaking noise added to a score. Small: it separates ties, it does not overrule a score. */
  private const val SCORE_JITTER = 0.08

  /** How much the layout pushes a quarter's grid chaos. A planned town is squarer at every kind, not only some. */
  private const val GRID_CHAOS_BIAS = 0.45
  private const val ORGANIC_CHAOS_BIAS = 1.0

  private const val SCORE_SALT = 0x62L
}
