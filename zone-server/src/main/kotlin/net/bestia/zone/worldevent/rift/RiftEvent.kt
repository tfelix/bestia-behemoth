package net.bestia.zone.worldevent.rift

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaGround
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.ProvinceSignals
import net.bestia.zone.worldevent.WorldEvent
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * A knot forms where the mana is thickest and the land around can feed it. The rift system takes it from there;
 * this tells the director how it ended. Draining the land until the knot dissolves, or sealing it, is the players'
 * win. A collapse, or being swallowed by another rift, is not.
 */
@Component
class RiftEvent(
  private val rifts: Rifts,
  private val field: ManaField,
  private val ground: ManaGround,
  private val config: RiftConfig,
  private val clock: BestiaClock,
  private val worldService: WorldService,
) : WorldEvent {

  override fun fits(signals: ProvinceSignals): Boolean {
    val peak = signals.peakCell ?: return false
    val feeds = synchronized(field) {
      RiftField(field, ground).ringDensity(peak.centreX, peak.centreY, config.knotRadiusMetres) >= config.dissolveBelow
    }
    return feeds && rifts.active().none { it.provinceIndex == signals.province.index }
  }

  override fun start(signals: ProvinceSignals): WorldEvent.Started? {
    val peak = signals.peakCell ?: return null
    val now = clock.now().absoluteSecond
    val random = Random(peak.key xor worldService.config.seed)
    val knotDays = config.knotMinBestiaDays + random.nextDouble() * (config.knotMaxBestiaDays - config.knotMinBestiaDays)

    val rift = rifts.formKnot(peak.centreX, peak.centreY, signals.province.index, now, now + (knotDays * DAY).toLong())
    val voxel = worldService.config.voxelSize
    return WorldEvent.Started(Vec3L((peak.centreX / voxel).toLong(), (peak.centreY / voxel).toLong(), 0), rift.id)
  }

  override fun isDefeated(instance: WorldEventInstance): Boolean =
    phaseOf(instance) in setOf(Phase.SEALED, Phase.DISSOLVED)

  override fun isOver(instance: WorldEventInstance): Boolean =
    phaseOf(instance) in setOf(Phase.COLLAPSED, Phase.MERGED)

  /** A GM cancel or the director's safety limit: the knot or rift dissolves and lets its mana go quietly. */
  override fun end(instance: WorldEventInstance) {
    val id = instance.subjectId ?: return
    val rift = rifts.active().firstOrNull { it.id == id } ?: return
    synchronized(field) {
      RiftField(field, ground).spill(rift.x, rift.y, 2 * config.knotRadiusMetres, rift.reservoir)
      rift.reservoir = 0.0
      rift.phase = Phase.DISSOLVED
    }
    rifts.saveAll(listOf(rift))
  }

  private fun phaseOf(instance: WorldEventInstance): Phase? = instance.subjectId?.let(rifts::phaseOf)

  private companion object {
    const val DAY = 86_400.0
  }
}
