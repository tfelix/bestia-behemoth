package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.GatherArea
import net.bestia.zone.ai.domain.bestia.Gatherable
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Notices what a gatherer could take in its area, nearest first. Only a bestia under GATHER orders has an area,
 * so for every other creature this costs one memory lookup.
 */
@Component
class GatherSense(
  private val aoi: EntityAOIService,
  private val kinds: PropKindRegistry,
  private val divergence: WorldObjectDivergenceRegistry,
) : Sense {

  override val name = "gather"

  override val intervalSeconds = 2f

  override val reads: ComponentClassSet = setOf(PropPose::class, StaticVisual::class, WorldObjectIdentity::class)

  override fun sense(context: SenseContext) {
    val area = context.recall(BestiaDomain.GATHER_AREA) ?: return

    val givenUp = context.recall(BestiaDomain.GIVEN_UP).orEmpty()
    val nearest = aoi.queryEntitiesInCube(area.center, area.radius * 2, AoiLayer.STATIC_ONLY)
      .mapNotNull { gatherableOf(context.world, it, area) }
      .filter { it.propId !in givenUp }
      .sortedBy { it.position.distance(context.position) }
      .take(MAX_REMEMBERED)

    context.remember(BestiaDomain.GATHERABLES, nearest)
  }

  private fun gatherableOf(world: World, id: EntityId, area: GatherArea): Gatherable? {
    val pose = world.get(id, PropPose::class) ?: return null
    if (!area.contains(pose.position)) return null

    val visual = world.get(id, StaticVisual::class) ?: return null
    val identity = world.get(id, WorldObjectIdentity::class) ?: return null
    // A prop with a divergence is used up or growing back; nothing a gatherer can take yet.
    if (divergence.of(identity.propId) != null) return null

    val work = workOn(visual) ?: return null

    return Gatherable(id, identity.propId, pose.position, work)
  }

  private fun workOn(visual: StaticVisual): Gatherable.Work? {
    return when {
      kinds.of(visual.kind).collect != null -> Gatherable.Work.COLLECT
      visual.kind in FELLED_KINDS -> Gatherable.Work.FELL
      else -> null
    }
  }

  private companion object {
    /** Enough for the planner to pick the nearest from; the rest is seen again once these are gone. */
    const val MAX_REMEMBERED = 8

    /** A blighted tree drops nothing, so it is not worth the swings. */
    val FELLED_KINDS = setOf(StaticEntityKind.TREE)
  }
}
