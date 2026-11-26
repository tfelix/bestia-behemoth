package net.bestia.zone.worldevent.rift

import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.EventGround
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import org.springframework.stereotype.Component
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * An open rift grows crystals around its gate: as many as the stage it reached, a new one now and then. Each drinks
 * from the rift, so breaking one takes a share of the reservoir with it, see [RiftCrystalHarvest]. Not persisted:
 * after a restart the rift grows them again.
 */
@Component
class RiftCrystals(
  private val config: RiftConfig,
  private val bestiaCatalogue: BestiaCatalogue,
  private val spawner: BestiaEntitySpawner,
  private val ground: EventGround,
  private val clock: BestiaClock,
) {

  private val grown = HashMap<Long, MutableList<EntityId>>()
  private val nextGrowthAt = HashMap<Long, Long>()

  /** The species the crystals are, for the harvest to tell them from the waves. */
  val speciesId: Long by lazy { bestiaCatalogue.byIdentifier(SPECIES).id }

  fun tend(world: World, rifts: List<StoredRift>, now: Long) {
    rifts.forEach { rift ->
      if (rift.phase == Phase.OPEN) {
        grow(world, rift, now)
      } else {
        clear(world, rift)
      }
    }
  }

  private fun grow(world: World, rift: StoredRift, now: Long) {
    val crystals = grown.getOrPut(rift.id) { ArrayList() }
    crystals.removeIf { !world.isAlive(it) }
    if (crystals.size >= rift.stage || now < (nextGrowthAt[rift.id] ?: 0L)) return

    val random = Random(rift.id * 31 + now)
    nextGrowthAt[rift.id] = now + (config.crystalRegrowRealMinutes * 60 * clock.speedFactor).toLong()
    val at = spotNear(rift, random) ?: return

    crystals += spawner.spawnMob(
      world, speciesId, at,
      persistent = false,
      homePosition = at,
      level = config.crystalLevelsPerStage * rift.stage,
    ).also { world.add(it, EventCreature(RIFT, rift.id)) }
  }

  private fun spotNear(rift: StoredRift, random: Random): Vec3L? {
    val angle = random.nextDouble() * 2 * Math.PI
    val distance = MIN_METRES + random.nextDouble() * (MAX_METRES - MIN_METRES)
    return ground.grounded(rift.x + cos(angle) * distance, rift.y + sin(angle) * distance)
  }

  private fun clear(world: World, rift: StoredRift) {
    nextGrowthAt.remove(rift.id)
    grown.remove(rift.id)?.forEach { if (world.isAlive(it)) world.destroy(it) }
  }

  private companion object {
    const val RIFT = "Rift"
    const val SPECIES = "rift_crystal"

    /** Near enough to be the gate's, far enough that the anchor and the waves do not stand on them. */
    const val MIN_METRES = 30.0
    const val MAX_METRES = 90.0
  }
}
