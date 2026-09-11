package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.construction.ConstructionSite
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.entity.VisualKind
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.PlayerStructureIdentity
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Puts a construction site into the world.
 *
 * Deliberately **not** [WorldObjectResidencyService.spawn], which every other structure goes through: that one
 * builds a static prop, and a site has to be an ordinary entity to be able to report its progress at all - see
 * [ConstructionSite]. Everything the two have in common is the row behind them.
 *
 * Components are attached inside the `createEntity` lambda for the reason `BestiaEntitySpawner` documents:
 * `World.add` defers to the end of the tick when called from inside a system, and a site placed mid-tick would
 * otherwise be briefly componentless.
 */
@Component
class ConstructionSiteSpawner {

  fun spawn(world: WorldView, entry: StructureEntry): EntityId {
    require(entry.isUnderConstruction) { "Structure ${entry.id} is finished and has no site to spawn" }

    val configure: World.(EntityId) -> Unit = { id ->
      // No `Grounded`: the position came off a client's raycast, so its z is a guess like every other
      // client-supplied one, and `ChunkStreamSystem.groundNewcomers` is what settles it.
      add(id, Position.fromVec3(entry.position))
      add(id, EntityVisual(VisualKind.STRUCTURE, entry.kind.ordinal.toLong()))
      add(id, Health(current = ConstructionSite.START_HP, max = ConstructionSite.START_HP))
      // The same baseline, for the same reason, that `PropPromotionService` gives a tree it makes attackable.
      add(id, StatusValues(strength = 1, intelligence = 1, vitality = 1, dexterity = 1, willpower = 1, agility = 1))
      add(id, PlayerStructureIdentity(entry.id))
      add(
        id,
        ConstructionSite(
          kind = entry.kind,
          ownerMasterId = entry.ownerMasterId,
          structureId = entry.id,
          yaw = entry.yaw,
          totalSeconds = entry.totalBuildSeconds,
          remainingSeconds = entry.remainingBuildSeconds
        )
      )
    }

    val entityId = world.createEntity(configure)

    LOG.debug { "Construction site for structure ${entry.id} (${entry.kind}) is entity $entityId" }

    return entityId
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
