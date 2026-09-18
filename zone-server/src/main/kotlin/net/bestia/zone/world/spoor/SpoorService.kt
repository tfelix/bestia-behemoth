package net.bestia.zone.world.spoor

import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.ground.ColumnKey
import net.bestia.zone.world.ground.GroundStampRegistry
import org.springframework.stereotype.Service

/**
 * Reads the tracks on a patch of ground.
 *
 * ### It owns no store of its own
 *
 * The prints are already there: `GroundStampRegistry` records one per tile anything walks over, with the
 * walker against it, and expires them on its own clock. So tracking is a *query*, not a second subsystem -
 * which is why there is no separate passage log, no second bound and no second thing to keep in step.
 *
 * The consequence worth knowing is that **a tracker can only read what a print could be left in**: rock, ice
 * and paving are absent from `SurfaceTrampleableGround.CAP_IMPRESSION`, so nothing walking over them records
 * anything. A road is the sensible way to travel unseen, which is the right answer arrived at by the right
 * mechanism rather than by a rule about roads.
 *
 * ### Tick-thread state, read through a world scope
 *
 * The registry is a plain map owned by the tick thread and a skill resolves on a background worker, so every
 * caller has to reach this through `SkillWorld.readTracks`, which opens a scope for exactly that reason.
 * Nothing here reads an ECS component, so the scope is about the lock and nothing else.
 */
@Service
class SpoorService(
  private val stamps: GroundStampRegistry,
  private val signatures: ActorSignatures,
  private val clock: BestiaClock,
) {

  private class Tally {
    var passages = 0
    var newestSecond = Long.MIN_VALUE
    val headings = IntArray(8)
  }

  /**
   * The heaviest traffic within [radiusTiles] of [centre], or null when the ground holds no tracks at all.
   *
   * A disc rather than the square of columns it is cut from, so the reach a skill level buys is the same in
   * every direction.
   */
  fun read(centre: Vec3L, radiusTiles: Long): TrackReading? {
    if (radiusTiles <= 0) return null

    val extent = stamps.chunkExtent
    val chunkSize = extent.toInt()
    val radiusSquared = radiusTiles * radiusTiles

    val tallies = HashMap<Long, Tally>()

    for (chunkY in chunkRange(centre.y, radiusTiles, extent)) {
      for (chunkX in chunkRange(centre.x, radiusTiles, extent)) {
        val column = stamps.stampsOf(ColumnKey.of(chunkX, chunkY)) ?: continue

        val originX = chunkX.toLong() * extent
        val originY = chunkY.toLong() * extent

        column.forEachStamp { cellIndex, octant, actorId, laidAtSecond ->
          val dx = originX + cellIndex % chunkSize - centre.x
          val dy = originY + cellIndex / chunkSize - centre.y

          if (dx * dx + dy * dy > radiusSquared) return@forEachStamp

          val tally = tallies.getOrPut(actorId) { Tally() }
          tally.passages++
          tally.headings[octant]++
          if (laidAtSecond > tally.newestSecond) tally.newestSecond = laidAtSecond
        }
      }
    }

    if (tallies.isEmpty()) return null

    // Most prints wins, and the fresher of two equals - which is what makes following a trail work: the thing
    // that walked through and the thing that lives here are told apart by how much of the ground they cover.
    val (actorId, heaviest) = tallies.entries
      .maxWith(compareBy({ it.value.passages }, { it.value.newestSecond }))

    return TrackReading(
      signature = signatures.of(actorId),
      passages = heaviest.passages,
      ageSeconds = (clock.now().absoluteSecond - heaviest.newestSecond).coerceAtLeast(0),
      octant = heaviest.headings.indices.maxBy { heaviest.headings[it] },
      walkers = tallies.size,
    )
  }

  private fun chunkRange(centre: Long, radiusTiles: Long, extent: Long): IntRange {
    return Math.floorDiv(centre - radiusTiles, extent).toInt()..Math.floorDiv(centre + radiusTiles, extent).toInt()
  }
}
