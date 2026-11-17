package net.bestia.zone.blackboard

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.blackboard.ecs.Blackboard
import net.bestia.zone.blackboard.ecs.BlackboardOffer
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.prop.ecs.construction.SiteCompletion
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Puts quest boards into the world. A board is an ordinary entity rather than a static prop, so it can carry
 * which quests are open. A player-built one keeps its structure row, so its town still counts the building.
 */
@Component
class Blackboards(
  private val structures: PlayerStructureService,
  private val structureRegistry: PlayerStructureRegistry,
) : SiteCompletion {

  override val kind = StaticEntityKind.BLACKBOARD

  override fun complete(world: World, siteEntityId: EntityId, site: ConstructionSite) {
    val built = structures.finishAsEntity(world, siteEntityId, site)
    if (built == null) {
      LOG.warn { "Blackboard site $siteEntityId has no position and cannot be finished" }
      return
    }

    raise(world, built.position, built.id)
    LOG.info { "Master ${site.ownerMasterId} finished a blackboard at ${built.position} (structure ${built.id})" }
  }

  fun raise(world: World, at: Vec3L, structureId: Long?): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.BLACKBOARD.ordinal.toLong()))
      add(id, Blackboard(structureId))
      add(id, BlackboardOffer())
    }
  }

  /** The boards players finished. Not stored as entities, so every boot raises them again. */
  fun built(): List<StructureEntry> {
    return structureRegistry.all().filter { it.kind == StaticEntityKind.BLACKBOARD && !it.isUnderConstruction }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
