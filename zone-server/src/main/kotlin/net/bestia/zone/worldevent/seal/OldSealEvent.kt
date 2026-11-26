package net.bestia.zone.worldevent.seal

import net.bestia.zone.casting.sense.BrewingEvents
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.prop.PropRemoval
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.mana.ManaTiers
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldboss.WorldTremors

import net.bestia.zone.worldevent.ProvinceSignals
import net.bestia.zone.worldevent.WorldEvent
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import net.bestia.zone.worldevent.rift.SealStones
import org.springframework.stereotype.Component

/**
 * Strong mana wears an old seal thin. The seal under the thickest mana in the province cracks, hums and is talked
 * about; a ritual held beside it within two Bestia days reinforces it. If nobody does, it breaks: a crystal frees the
 * world boss it holds, a seal stone spills the mana its rift gathered.
 */
@Component
class OldSealEvent(
  private val seals: OldSeals,
  private val cracking: CrackingSeals,
  private val provinces: ProvinceService,
  private val tiers: ManaTiers,
  private val clock: BestiaClock,
  private val residency: WorldObjectResidencyService,
  private val removal: PropRemoval,
  private val spawner: BestiaEntitySpawner,
  private val stones: SealStones,
  private val mana: ManaConcentrationService,
  private val rumours: RumourService,
  private val worldService: WorldService,
  private val world: WorldView,
  private val tremors: WorldTremors,
) : WorldEvent {

  override fun fits(signals: ProvinceSignals): Boolean = weakestIn(signals) != null

  override fun start(signals: ProvinceSignals): WorldEvent.Started? {
    val seal = weakestIn(signals) ?: return null
    cracking.watch(seal.subjectId, seal.at)
    rumours.post(RumourKind.SEAL_CRACKING, seal.at.x, seal.at.y, RUMOUR_STRENGTH)
    return WorldEvent.Started(seal.at, seal.subjectId)
  }

  override fun isDefeated(instance: WorldEventInstance): Boolean {
    val subjectId = instance.subjectId ?: return false
    // After a restart the watch is rebuilt here, before any ritual could count.
    cracking.watch(subjectId, Vec3L(instance.x, instance.y, instance.z))
    if (!cracking.isReinforced(subjectId)) return false

    cracking.forget(subjectId)
    rumours.post(RumourKind.SEAL_REINFORCED, instance.x, instance.y, RUMOUR_STRENGTH)
    return true
  }

  /** It broke: nobody reinforced it in time. */
  override fun isOver(instance: WorldEventInstance): Boolean {
    if (clock.now().absoluteSecond < breaksAtSecond(instance)) return false

    instance.subjectId?.let { subjectId ->
      cracking.forget(subjectId)
      seals.of(subjectId)?.let(::breakSeal)
    }
    rumours.post(RumourKind.SEAL_BROKEN, instance.x, instance.y, BROKEN_STRENGTH)
    return true
  }

  override fun stageOf(instance: WorldEventInstance): BrewingEvents.Stage = BrewingEvents.Stage.GATHERING

  override fun changesAtSecond(instance: WorldEventInstance): Long = breaksAtSecond(instance)

  /** Standing guard by the seal holds the event's ground. */
  override fun heldArea(instance: WorldEventInstance): WorldEvent.HeldArea =
    WorldEvent.HeldArea(instance.x.toDouble(), instance.y.toDouble(), HELD_RADIUS_METRES)

  /** A GM called it off: the seal stays as it was. */
  override fun end(instance: WorldEventInstance) {
    instance.subjectId?.let(cracking::forget)
  }

  /** The seal under the thickest mana, if any is under enough and old enough. */
  private fun weakestIn(signals: ProvinceSignals): OldSeal? {
    val now = clock.now().absoluteSecond
    return seals.all()
      .filter { provinces.provinceAt(it.at.x.toDouble(), it.at.y.toDouble())?.index == signals.province.index }
      .filter { it !is OldSeal.Stone || now - it.stone.sealedAtSecond >= STONE_AGE_SECONDS }
      .map { it to tiers.tierAt(it.at) }
      .filter { (_, tier) -> tier >= WEARS_FROM }
      .maxByOrNull { (_, tier) -> tier }
      ?.first
  }

  private fun breaksAtSecond(instance: WorldEventInstance): Long = instance.startedAtSecond + CRACKS_FOR_SECONDS

  private fun breakSeal(seal: OldSeal) {
    world.read {
      when (seal) {
        is OldSeal.Crystal -> freeBoss(this, seal)
        is OldSeal.Stone -> spill(this, seal)
      }
    }
  }

  /** A crystal someone can see dies like one struck down, which frees the boss; one nobody can see goes unseen. */
  private fun freeBoss(world: World, crystal: OldSeal.Crystal) {
    val standing = residentIn(world, crystal.at).firstOrNull { world.get(it, WorldObjectIdentity::class)?.propId == crystal.propId }
    if (standing != null) {
      world.add(standing, Dead())
      return
    }
    removal.removedUnseen(crystal.propId, StaticEntityKind.FIXTURE_SEAL_CRYSTAL, PropRemoval.Cause.GONE_FOR_GOOD)
    spawner.spawnMob(world, crystal.bossId, crystal.at, persistent = true)
    tremors.feel(world, crystal.at)
  }

  private fun spill(world: World, stone: OldSeal.Stone) {
    residentIn(world, stone.at)
      .firstOrNull {
        world.get(it, StaticVisual::class)?.kind == StaticEntityKind.SEAL_STONE &&
          world.get(it, Position::class)?.toVec3L() == stone.at
      }
      ?.let { residency.remove(world, it) }
    stones.remove(stone.stone)
    mana.release(stone.at, stone.stone.motes, SPILL_RADIUS_METRES)
  }

  private fun residentIn(world: World, at: Vec3L): List<Long> {
    val size = worldService.config.chunkSize.toLong()
    return residency.entitiesIn(Math.floorDiv(at.x, size).toInt(), Math.floorDiv(at.y, size).toInt()).toList()
  }

  private companion object {
    /** Below this, the mana is not strong enough to wear a seal. */
    val WEARS_FROM = ManaTier.UNEASY

    /** A seal stone is new for a month of Bestia days. */
    const val STONE_AGE_SECONDS = 30 * 24 * 3600L

    /** Two Bestia days, about sixteen real hours, from the first crack to the break. */
    const val CRACKS_FOR_SECONDS = 2 * 24 * 3600L

    const val HELD_RADIUS_METRES = 60.0

    /** As a rift that collapses, over the same ground. */
    const val SPILL_RADIUS_METRES = 300.0

    const val RUMOUR_STRENGTH = 0.6
    const val BROKEN_STRENGTH = 0.9
  }
}
