package net.bestia.zone.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import java.time.Clock
import org.springframework.stereotype.Component as SpringComponent

/**
 * Lets campfires burn down: a fire that went out shows it, and one left cold too long is gone.
 *
 * Reads the registry rather than the world, because a fire burns down whether or not anybody stands near enough
 * for it to be in the world. Only the look of a loaded one is the world's business.
 */
@SpringComponent
class CampfireSystem(
  private val structures: PlayerStructureRegistry,
  private val source: PlayerStructureSource,
  private val residency: WorldObjectResidencyService,
  private val worldService: WorldService,
  private val clock: Clock = Clock.systemUTC()
) : System {

  override val phase = Phase.WORLD
  override val schedule: Schedule = Schedule.EverySeconds(5f)

  // After the props of the tick are in place, so a fire is swapped for its other look on settled ground.
  override val after = setOf(WorldObjectResidencySystem::class, PropSupportSystem::class)
  override val reads: ComponentClassSet = setOf(PlayerStructureIdentity::class, StaticVisual::class)

  // Empty: a look changes by putting a new entity in place of the old one, and the registry is off the ECS.
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    val now = clock.instant()
    val loaded = loadedCampfires(world)

    structures.all()
      .filter { it.kind == StaticEntityKind.CAMPFIRE && !it.isUnderConstruction }
      .forEach { entry ->
        val entityId = loaded[entry.id]

        if (entry.isGoneAt(now)) {
          entityId?.let { residency.remove(world, it) }
          removeRow(entry)
          LOG.info { "Campfire ${entry.id} went cold and is gone" }
        } else if (entityId != null && world.get(entityId, StaticVisual::class)?.variant != Campfire.variantAt(entry, now)) {
          residency.remove(world, entityId)
          residency.placeNow(world, source.siteOf(entry))
        }
      }
  }

  /** Structure id -> the entity standing for it, for every campfire somebody is near enough to see. */
  private fun loadedCampfires(world: World): Map<Long, EntityId> {
    val loaded = HashMap<Long, EntityId>()

    world.query(PlayerStructureIdentity::class, StaticVisual::class).each { id ->
      if (get<StaticVisual>().kind == StaticEntityKind.CAMPFIRE) loaded[get<PlayerStructureIdentity>().structureId] = id
    }

    return loaded
  }

  private fun removeRow(entry: StructureEntry) {
    val chunkSize = worldService.config.chunkSize.toLong()
    structures.remove(
      structureId = entry.id,
      chunkX = Math.floorDiv(entry.position.x, chunkSize).toInt(),
      chunkY = Math.floorDiv(entry.position.y, chunkSize).toInt()
    )
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
