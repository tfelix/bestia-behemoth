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
 * ### Why it equals the view volume exactly
 *
 * A player is sent terrain for [ChunkStreamConfig.chunksAcrossView] chunks square. Anything inside
 * that is ground they can see, so it is ground an entity can be seen standing on; anything outside it
 * is ground they do not have. Making the entity range a *different* number means picking which of the
 * two mistakes to make - entities popping in over terrain that arrived long ago, or updates spent on
 * entities standing on ground the client cannot draw.
 *
 * So there is deliberately no margin. If hysteresis is ever wanted - so an entity leaving the view
 * keeps updating for a moment rather than stopping at the boundary - the honest unit is one chunk of
 * slack added here, once, rather than a second constant somewhere that drifts.
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
   * itself. At the defaults this is 11 chunks x 32 voxels = 352, so +/-176 m, which is the view
   * volume to the metre.
   */
  val cubeEdge: Long
    get() = settings.chunksAcrossView.toLong() *
        worldService.config.chunkSize / ChunkCoords.VOXELS_PER_POSITION_UNIT
}
