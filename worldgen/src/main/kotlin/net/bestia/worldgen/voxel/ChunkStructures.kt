package net.bestia.worldgen.voxel

import net.bestia.worldgen.civ.BridgeChannels
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.resource.GradeMix
import net.bestia.worldgen.resource.OreBody
import net.bestia.worldgen.resource.OreGrade
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ore in the rock of one chunk, sampled from the sparse deposits that reach it.
 *
 * This is the payoff of storing deposits as points. Nothing anywhere holds a per-voxel ore field; the world
 * tier holds a hundred thousand markers, chunk generation asks the index which of them reach this chunk -
 * typically none, occasionally one - and the ore materialises from the marker's own attributes.
 *
 * Whether a particular voxel is ore is decided by hashing its **world position** together with the deposit's
 * id, never by a chunk seed. That is the difference between an orebody that two adjacent chunks agree about
 * and one that changes shape at every chunk border - and an orebody is exactly the kind of thing a player
 * digs across a border and notices.
 *
 * The shape itself comes from [OreBody] rather than from constants here, because the world tier used the same
 * form in reverse to decide how big the body had to be to hold the tonnage it advertises. Two copies of that
 * geometry would be a quiet way for a deposit to contain a different amount of metal than it claims.
 */
class OreVeins(
  features: List<VectorFeature>,
  private val seed: Long,
  private val grades: GradeMix,
  /**
   * The corruption field, or null on a pipeline without the corruption stage.
   *
   * Sampled **once per deposit, here in the constructor**, not per voxel. That is the whole of why aetherite
   * needs no new deposit kind and no cycle in the stage graph: the body is iron or copper as placed, and what
   * you dig out of it is decided by the rock around it. Per voxel it would also be wrong - a body straddling
   * a corruption fringe would come out half metal and half aetherite, and a seam that changes metal halfway
   * along is not a thing.
   */
  private val corruption: FloatLayer? = null,
  /** Corruption at or above which a body yields aetherite. `CorruptionParams.aetheriteCorruption`. */
  private val aetheriteCorruption: Double = 1.0
) {

  private class Body(
    val idSalt: Long,
    val x: Double,
    val y: Double,
    val small: BlockType,
    val medium: BlockType,
    val rich: BlockType,
    val radius: Double,
    val depth: Double,
    val richness: Double
  ) {
    /** Vertical half-extent. Orebodies are flatter than they are wide, which is what makes seams seams. */
    val halfHeight get() = radius * OreBody.VERTICAL_FLATTENING
  }

  private val bodies: List<Body> = features
    .asSequence()
    .filter { it.kind == FeatureKind.ORE_DEPOSIT }
    .filterIsInstance<PointMarker>()
    .mapNotNull { marker ->
      runCatching {
        val type = ResourceType.entries[marker.attribute(DepositChannels.TYPE).toInt()]
        val corrupted = (corruption?.sampleBilinear(marker.position.x, marker.position.y) ?: 0.0) >=
            aetheriteCorruption
        val blocks = blocksFor(if (corrupted) ResourceType.AETHERITE else type)
          ?: return@runCatching null
        Body(
          idSalt = marker.id.value,
          x = marker.position.x,
          y = marker.position.y,
          small = blocks[0],
          medium = blocks[1],
          rich = blocks[2],
          radius = marker.attribute(DepositChannels.RADIUS),
          depth = marker.attribute(DepositChannels.DEPTH),
          richness = marker.attribute(DepositChannels.RICHNESS)
        )
      }.getOrNull()
    }
    .filterNotNull()
    .toList()

  val isEmpty get() = bodies.isEmpty()

  /**
   * The ore block at a voxel, or null when there is none there.
   *
   * @param surface terrain height of the column, so a deposit's depth is measured from the ground rather
   *   than from sea level - a seam two hundred metres down follows the hillside above it
   */
  fun blockAt(worldX: Double, worldY: Double, elevation: Double, surface: Double): BlockType? {
    if (bodies.isEmpty()) return null

    val qx = Math.round(worldX * QUANTISE)
    val qy = Math.round(worldY * QUANTISE)
    val qz = Math.round(elevation * QUANTISE)

    for (body in bodies) {
      val dx = worldX - body.x
      val dy = worldY - body.y
      val horizontal = sqrt(dx * dx + dy * dy)
      if (horizontal > body.radius) continue

      val centre = surface - body.depth
      val vertical = abs(elevation - centre)
      if (vertical > body.halfHeight) continue

      // Densest at the middle and fading to nothing at the edge, so an orebody has a gradient rather than a
      // hard rim - which is what makes following a vein a thing a player can do.
      val fade = (1.0 - horizontal / body.radius) * (1.0 - vertical / body.halfHeight)
      val chance = body.richness * fade

      val roll = GenRng.hashUnit(seed, body.idSalt, qx, qy, qz)
      if (roll >= chance) continue

      // A second, independent roll for the grade. Independent of the fade on purpose: the world tier sized
      // this body by assuming an average voxel yields `grades.meanYieldKg`, and that assumption only holds
      // while the mix is the same everywhere in the body.
      return when (grades.gradeAt(GenRng.hashUnit(seed, body.idSalt, GRADE_STREAM, qx, qy, qz))) {
        OreGrade.SMALL -> body.small
        OreGrade.MEDIUM -> body.medium
        OreGrade.RICH -> body.rich
      }
    }

    return null
  }

  private companion object {
    /**
     * Fixed-point resolution for hashing a position, in units per metre.
     *
     * Quantising before hashing is the discipline from the architecture document applied to a discrete
     * decision: "is this voxel ore" is a branch, so its input goes through a quantisation step first, and then
     * every node deciding it for the same voxel decides the same way regardless of how the coordinate was
     * arrived at.
     */
    const val QUANTISE = 100.0

    /** Salt separating the grade roll from the is-it-ore roll, so the two are not the same number. */
    const val GRADE_STREAM = 0x67ADEL

    /**
     * The three grade blocks for a resource, or null when it does not show as gradeable ore.
     *
     * Marble and clay come back as a single plain material repeated three times: they are quarried by the
     * cubic metre rather than picked up by the kilogram, so a grade would be a number nobody could spend, and
     * the caller does not need to know they are a special case.
     */
    fun blocksFor(type: ResourceType): List<BlockType>? =
      OreBlocks.blocksFor(type) ?: OreBlocks.plainBlockFor(type)?.let { listOf(it, it, it) }
  }
}

/**
 * Bridge decks over one chunk.
 *
 * A bridge is the one structure a heightfield genuinely cannot express, because a heightfield has one height
 * per column and a bridge is a surface with air underneath it. So the road feature leaves a gap at the
 * crossing - which on its own is a ford - and the deck is written here, as blocks, above whatever the column
 * already contains.
 *
 * The marker carries its own bearing, span and half-width, so laying the deck needs nothing but the marker:
 * no lookup of the road it belongs to, no agreement between chunks beyond the marker they both queried.
 */
class BridgeDecks(features: List<VectorFeature>) {

  private class Deck(
    val x: Double,
    val y: Double,
    val bearingX: Double,
    val bearingY: Double,
    val halfSpan: Double,
    val halfWidth: Double,
    val elevation: Double
  )

  private val decks: List<Deck> = features
    .asSequence()
    .filter { it.kind == FeatureKind.BRIDGE }
    .filterIsInstance<PointMarker>()
    .mapNotNull { marker ->
      runCatching {
        Deck(
          x = marker.position.x,
          y = marker.position.y,
          bearingX = marker.attribute(BridgeChannels.BEARING_X),
          bearingY = marker.attribute(BridgeChannels.BEARING_Y),
          halfSpan = marker.attribute(BridgeChannels.SPAN) * 0.5,
          halfWidth = marker.attribute(BridgeChannels.HALF_WIDTH),
          elevation = marker.attribute(BridgeChannels.DECK_ELEVATION)
        )
      }.getOrNull()
    }
    .toList()

  val isEmpty get() = decks.isEmpty()

  /**
   * Elevation of the deck surface over a column, or [Double.NaN] where no deck covers it.
   *
   * Tested against an oriented rectangle rather than a disc, because a bridge is a rectangle: it spans the
   * water along the road's bearing and is only a carriageway wide across it. A disc would put decking out over
   * the water either side of the road.
   */
  fun deckAt(worldX: Double, worldY: Double): Double {
    for (deck in decks) {
      val dx = worldX - deck.x
      val dy = worldY - deck.y

      val along = dx * deck.bearingX + dy * deck.bearingY
      if (abs(along) > deck.halfSpan) continue

      // The road's left normal, which is the bearing turned ninety degrees.
      val across = dx * -deck.bearingY + dy * deck.bearingX
      if (abs(across) > deck.halfWidth) continue

      return deck.elevation
    }

    return Double.NaN
  }

  /** Thickness of the decking in metres. Two courses of masonry, so it reads as a structure. */
  val thickness: Double get() = DECK_THICKNESS

  private companion object {
    const val DECK_THICKNESS = 1.5
  }
}
