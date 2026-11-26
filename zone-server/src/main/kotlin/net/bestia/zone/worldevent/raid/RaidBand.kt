package net.bestia.zone.worldevent.raid

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.spawn.ecs.WildSpawnerService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * A band of brigands and its chief, as strong as the bestia its province breeds. The chief is what the raid is
 * about and outlives a restart; the band does not, and gathers around the chief again. On the tick.
 */
@Component
class RaidBand(
  private val dens: WildSpawnerService,
  private val bestiaCatalogue: BestiaCatalogue,
  private val provinces: ProvinceService,
  private val spawner: BestiaEntitySpawner,
  private val deletionQueue: PersistedEntityDeletionQueue,
) {

  /** The band by its chief. */
  private val bands = HashMap<EntityId, MutableList<EntityId>>()

  /** Spawns the chief and the band at [from], heading [home]. Every one counts for the [event]'s contract. */
  fun muster(world: World, event: String, provinceIndex: Int, from: Vec3L, home: Vec3L): EntityId {
    val level = levelIn(provinceIndex)
    val chief = spawner.spawnMob(world, brigand().id, from, homePosition = home, level = level + CHIEF_LEVELS)
    world.add(chief, EventCreature(event, chief))
    bands[chief] = recruit(world, event, chief, from, home, level)
    return chief
  }

  /** Sends the chief and the band to [home], and gathers a band again after a restart. */
  fun lead(world: World, event: String, provinceIndex: Int, chief: EntityId, home: Vec3L) {
    if (!world.isAlive(chief)) return
    val band = bands.getOrPut(chief) {
      val at = world.get(chief, Position::class)?.toVec3L() ?: home
      recruit(world, event, chief, at, home, levelIn(provinceIndex))
    }
    (band + chief).filter(world::isAlive).forEach { headFor(world, it, home) }
  }

  /** Whether the chief and everyone in the band fell. */
  fun isBroken(world: World, chief: EntityId): Boolean =
    !world.isAlive(chief) && bands[chief].orEmpty().none(world::isAlive)

  /** Takes the chief and the band out of the world. */
  fun disband(world: World, chief: EntityId) {
    bands.remove(chief)?.filter(world::isAlive)?.forEach(world::destroy)
    deletionQueue.enqueue(chief)
    if (world.isAlive(chief)) world.destroy(chief)
  }

  private fun recruit(world: World, event: String, chief: EntityId, from: Vec3L, home: Vec3L, level: Int): MutableList<EntityId> =
    MutableList(BAND_SIZE) {
      spawner.spawnMob(world, brigand().id, from, persistent = false, homePosition = home, level = level)
        .also { world.add(it, EventCreature(event, chief)) }
    }

  private fun headFor(world: World, brigand: EntityId, home: Vec3L) {
    world.get(brigand, AiAgent::class)?.memory?.set(BestiaDomain.HOME_POSITION, home, Blackboard.PERMANENT)
  }

  /** The mean top level of the province's den species, so the band fights like the land around it. */
  private fun levelIn(provinceIndex: Int): Int =
    dens.dens
      .filter { provinces.provinceAt(it.position.x.toDouble(), it.position.y.toDouble())?.index == provinceIndex }
      .mapNotNull { bestiaCatalogue.find(it.bestiaId) }
      .filterNot { it.boss || it.worldBoss || it.eventOnly }
      .map { it.levelMax }
      .average()
      .takeIf { !it.isNaN() }
      ?.roundToInt()
      ?: brigand().levelMax

  private fun brigand() = bestiaCatalogue.byIdentifier(BRIGAND)

  private companion object {
    const val BRIGAND = "brigand"
    const val BAND_SIZE = 5

    /** Above the band, like an alpha above its pack. */
    const val CHIEF_LEVELS = 8
  }
}
