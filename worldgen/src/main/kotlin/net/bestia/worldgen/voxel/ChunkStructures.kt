package net.bestia.worldgen.voxel

import net.bestia.worldgen.civ.BridgeChannels
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.resource.DepositChannels
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
 */
class OreVeins(features: List<VectorFeature>, private val seed: Long) {

  private class Body(
    val idSalt: Long,
    val x: Double,
    val y: Double,
    val block: BlockType,
    val radius: Double,
    val depth: Double,
    val richness: Double
  ) {
    /** Vertical half-extent. Orebodies are flatter than they are wide, which is what makes seams seams. */
    val halfHeight get() = radius * VERTICAL_FLATTENING
  }

  private val bodies: List<Body> = features
    .asSequence()
    .filter { it.kind == FeatureKind.ORE_DEPOSIT }
    .filterIsInstance<PointMarker>()
    .mapNotNull { marker ->
      runCatching {
        val type = ResourceType.entries[marker.attribute(DepositChannels.TYPE).toInt()]
        val block = blockFor(type) ?: return@runCatching null
        Body(
          idSalt = marker.id.value,
          x = marker.position.x,
          y = marker.position.y,
          block = block,
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

      val roll = GenRng.hashUnit(
        seed,
        body.idSalt,
        Math.round(worldX * QUANTISE),
        Math.round(worldY * QUANTISE),
        Math.round(elevation * QUANTISE)
      )
      if (roll < chance) return body.block
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

    /** Vertical extent of an orebody relative to its radius. Below 1 makes it a seam rather than a blob. */
    const val VERTICAL_FLATTENING = 0.45

    fun blockFor(type: ResourceType): BlockType? = when (type) {
      ResourceType.COPPER -> BlockType.ORE_COPPER
      ResourceType.TIN -> BlockType.ORE_TIN
      ResourceType.IRON -> BlockType.ORE_IRON
      ResourceType.GOLD_LODE, ResourceType.GOLD_PLACER -> BlockType.ORE_GOLD
      ResourceType.SILVER -> BlockType.ORE_SILVER
      ResourceType.COAL -> BlockType.COAL_SEAM
      ResourceType.SALT -> BlockType.ROCK_SALT
      ResourceType.MARBLE -> BlockType.LIMESTONE
      ResourceType.CLAY -> BlockType.CLAY
      // Stone, timber, furs and fish are not things in the rock. They are surface resources, and putting a
      // block down for them would be inventing geology to represent a forest.
      ResourceType.STONE, ResourceType.TIMBER, ResourceType.FURS, ResourceType.FISH -> null
    }
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
