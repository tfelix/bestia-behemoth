package net.bestia.zone.world.stream

import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * How far entity traffic reaches, derived from how far terrain reaches.
 *
 * ### Why this is one number and not two constants
 *
 * It used to be two, they disagreed with each other and both disagreed with the terrain:
 *
 * - `OutMessageProcessor.UPDATE_RANGE` was `100 * 100`, carrying a comment claiming one metre is a
 *   hundred position units. [ChunkCoords] is the authority on that and says one unit is one voxel is
 *   one metre - so the constant was not a hundred-metre range, it was a **ten-kilometre cube**, and
 *   every public component update was broadcast to a quarter of the world.
 * - `GetAllEntitiesHandler.ENTITY_QUERY_RANGE` was `30`, and
 *   [net.bestia.zone.ecs.AreaOfInterestService] halves what it is given, so the initial snapshot a
 *   client asked for on login covered fifteen metres.
 *
 * Between them an entity was invisible until it moved and then visible from five kilometres away.
 * That is survivable while everything of interest is a mob that moves - it is why the defect went
 * unnoticed - and it is fatal for a static entity, which is announced once and then never again.
 *
 * ### Why it is its own setting rather than the view radius
 *
 * It used to read [ChunkStreamConfig.chunksAcrossView] directly, and the argument for that was sound
 * while terrain arrived at one detail level: ground the client has is ground an entity can be seen
 * standing on, and any other number picks which of two mistakes to make.
 *
 * Terrain now arrives at two. Past the full-detail ring the client gets a coarse surface it can draw
 * but cannot dig, collide with or interact with - so "ground the client has" stopped being one radius
 * and stopped answering this question. Entities belong to the *full-detail* ring, which is what this
 * number is: raising the draw distance must not multiply entity traffic by the square of it.
 *
 * Still no margin, and still one number. If hysteresis is ever wanted - so an entity leaving the view
 * keeps updating for a moment rather than stopping at the boundary - the honest unit is one chunk of
 * slack added to this setting, rather than a second constant somewhere that drifts.
 *
 * Computed on each read rather than cached: `chunkSize` is a per-world birth setting, and
 * `WorldProvisioning.recreate` can replace the world under a running server.
 */
@Service
class InterestRange(
  private val worldService: WorldService,
  private val settings: ChunkStreamConfig
) {

  /**
   * Edge length of the interest cube, in position units.
   *
   * The *edge*, not the radius, because that is what
   * [net.bestia.zone.ecs.AreaOfInterestService.queryEntitiesInCube] takes - it halves the value
   * itself. At the defaults this is 11 chunks x 32 voxels = 352, so +/-176 m, which is the
   * full-detail terrain ring to the metre.
   */
  val cubeEdge: Long
    get() = settings.chunksAcrossInterest.toLong() *
        worldService.config.chunkSize / ChunkCoords.VOXELS_PER_POSITION_UNIT
}
