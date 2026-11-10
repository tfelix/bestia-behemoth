package net.bestia.zone.fishing

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.GearWear
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.ecs.attack.AttackCancelService
import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.update
import net.bestia.zone.fishing.ecs.Fishing
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.navigation.region.RegionGrid
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.Random

/**
 * Casting a line and striking it. The wait and the bite in between are [net.bestia.zone.fishing.ecs.FishingSystem]'s.
 */
@Service
class FishingService(
  private val catalogue: FishingCatalogue,
  private val water: RegionGrid,
  private val gearWear: GearWear,
  private val castCancel: CastCancelService,
  private val attackCancel: AttackCancelService,
  private val timingCheck: StrikeTimingCheck,
  private val random: Random = ThreadLocalRandomSource,
  private val nanoTime: () -> Long = System::nanoTime,
) {

  /** Null when the line is out, else why it is not. */
  fun cast(world: World, entityId: EntityId, target: Vec3L): OpError? {
    val rod = rodInHand(world, entityId)
    val position = world.get(entityId, Position::class)?.toVec3L()
    if (!world.has(entityId, Master::class) || rod == null || position == null) {
      // The client only casts for a master with a whole rod in hand.
      LOG.warn { "Refused a cast by entity $entityId: not a master with a rod in hand" }
      return OpError.REQUEST_REFUSED
    }

    if (position.distance(target) > catalogue.rules.reachMetres) {
      return OpError.FISHING_TOO_FAR
    }
    if (!water.isWaterAt(target)) {
      return OpError.FISHING_NOT_WATER
    }

    castCancel.interrupt(world, entityId)
    attackCancel.cancelAttack(world, entityId)
    gearWear.wearWeapon(world, entityId, ROD_WEAR_PER_CAST)
    world.add(entityId, Fishing(target, position, rod.uniqueId, randomWaitSeconds()))

    return null
  }

  /** A strike during the bite lands a catch; any other strike only pulls the line in. */
  fun strike(world: World, entityId: EntityId) {
    val fishing = world.remove(entityId, Fishing::class) ?: return
    if (!fishing.isBiting) {
      return
    }

    timingCheck.record(world, entityId, (nanoTime() - fishing.biteStartedNanos) / NANOS_PER_MILLI)

    val rod = rodInHand(world, entityId) ?: return
    val maxCatchLevel = catalogue.maxCatchLevelOf(rod.itemId) ?: return
    val catch = catalogue.table.draw(maxCatchLevel, catchLevelCapOf(world, entityId), random) ?: return

    land(world, entityId, catch)
  }

  private fun land(world: World, entityId: EntityId, catch: CatchTable.Entry) {
    // Appended rather than added on its own: one CreateItemIntent fits on an entity, and a second grant this
    // tick would replace it.
    val pending = world.get(entityId, ObtainItemIntent.CreateItemsIntent::class)?.grants.orEmpty()
    world.add(entityId, ObtainItemIntent.CreateItemsIntent(pending + ObtainItemIntent.CreateItemIntent(catch.itemId, 1)))

    world.update(entityId, { GainExp() }) { it.value += gatheringExpOf(catch.level) }
  }

  private fun catchLevelCapOf(world: World, entityId: EntityId): Int {
    val fishingRank = catalogue.fishingSkillId
      ?.let { world.get(entityId, KnownSkills::class)?.levelOf(it) }
      ?: 0

    return catalogue.catchLevelCap(fishingRank)
  }

  /** The rod in the right hand, or null for anything else, a broken rod included. */
  fun rodInHand(world: World, entityId: EntityId): Equipment.EquippedItem? {
    val held = world.get(entityId, Equipment::class)?.get(EquipmentSlot.RIGHT_HAND) ?: return null
    if (held.isBroken || catalogue.maxCatchLevelOf(held.itemId) == null) {
      return null
    }

    return held
  }

  private fun randomWaitSeconds(): Float {
    val rules = catalogue.rules

    return rules.minWaitSeconds + random.nextFloat() * (rules.maxWaitSeconds - rules.minWaitSeconds)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val ROD_WEAR_PER_CAST = 1
    const val NANOS_PER_MILLI = 1_000_000L

    /** The gathering row of the experience table on the design docs' bestia page. */
    fun gatheringExpOf(catchLevel: Int): Int {
      return 2 * catchLevel + 5
    }
  }
}
