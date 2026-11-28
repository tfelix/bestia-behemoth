package net.bestia.zone.fishing

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.GearWear
import net.bestia.zone.battle.RangedWeapon
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.ammo.AmmoType
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.ecs.attack.AttackCancelService
import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.gear.ToolTerm
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.update
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.fishing.ecs.Fishing
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
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
  private val ground: FishingGround,
  private val ammo: AmmoSupply,
  private val strongbox: SunkenStrongbox,
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
    val bait = hookBait(world, entityId)
    world.add(entityId, Fishing(target, position, rod.uniqueId, randomWaitSeconds(world, entityId, bait), bait?.itemId))

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
    if (strongbox.isHauled() && haulStrongbox(world, entityId, fishing)) {
      return
    }

    val spot = ground.spotAt(fishing.castFrom, fishing.bobber)
    val favoured = favouredBy(world, entityId, fishing.baitItemId)
    val catch = catalogue.table.draw(maxCatchLevel, catchLevelCapOf(world, entityId), random, spot, favoured) ?: return

    land(world, entityId, catch.itemId, catch.level)
  }

  /** The strongbox's gear, rolled now from the country round the bobber. False when nothing fits it. */
  private fun haulStrongbox(world: World, entityId: EntityId, fishing: Fishing): Boolean {
    val masterId = world.get(entityId, Master::class)?.masterId ?: return false
    val levels = ground.levelsAt(fishing.bobber) ?: return false
    val contents = strongbox.contentsFor(levels) ?: return false

    strongbox.haulUp(entityId, masterId, contents)
    world.update(entityId, { GainExp() }) { it.value += gatheringExpOf(levels.last) }

    return true
  }

  private fun land(world: World, entityId: EntityId, itemId: Long, level: Int) {
    // Appended rather than added on its own: one CreateItemIntent fits on an entity, and a second grant this
    // tick would replace it.
    val pending = world.get(entityId, ObtainItemIntent.CreateItemsIntent::class)?.grants.orEmpty()
    world.add(entityId, ObtainItemIntent.CreateItemsIntent(pending + ObtainItemIntent.CreateItemIntent(itemId, 1)))

    world.update(entityId, { GainExp() }) { it.value += gatheringExpOf(level) }
  }

  /** The first bait in the quiver the bag still holds, taken off it onto the hook. Null to fish bare. */
  private fun hookBait(world: World, entityId: EntityId): FishingCatalogue.Bait? {
    val ammunition = ammo.nextFor(world, entityId, BAIT_LINE) ?: return null
    val bait = catalogue.baitOf(ammunition.itemId) ?: return null
    if (!ammo.draw(world, entityId, ammunition)) {
      return null
    }

    return bait
  }

  /** What the bait on the hook and a helper on the bank draw, each tag's factors multiplied. */
  private fun favouredBy(world: World, entityId: EntityId, baitItemId: Long?): Map<CatchTag, Double> {
    val favoured = HashMap<CatchTag, Double>()
    baitItemId?.let(catalogue::baitOf)?.favoured?.forEach { (tag, factor) -> favoured.merge(tag, factor, Double::times) }
    if (hasHelperNear(world, entityId)) {
      favoured.merge(CatchTag.TREASURE, catalogue.helper.treasureFactor, Double::times)
    }

    return favoured
  }

  /** Whether one of the master's own water bestias stands alive near the angler. */
  fun hasHelperNear(world: World, entityId: EntityId): Boolean {
    val masterId = world.get(entityId, Master::class)?.masterId ?: return false
    val at = world.get(entityId, Position::class)?.toVec3L() ?: return false
    val range = catalogue.helper.rangeMetres

    return OwnedBestia.ownedBy(world, masterId).any { owned ->
      val bestia = owned.entityId
      world.get(bestia, Nature::class)?.kind == BestiaKind.AQUATIC &&
          !world.has(bestia, Dead::class) &&
          world.get(bestia, Position::class)?.toVec3L()?.let { at.distance(it) <= range } == true
    }
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

  private fun randomWaitSeconds(world: World, entityId: EntityId, bait: FishingCatalogue.Bait?): Float {
    val rules = catalogue.rules
    val essenceCut = world.get(entityId, CombatStats::class)?.values?.toolPercent(ToolTerm.BITE_WAIT_CUT) ?: 0
    val baitCut = bait?.waitCutPercent ?: 0
    val helperCut = if (hasHelperNear(world, entityId)) catalogue.helper.waitCutPercent else 0
    val cut = (essenceCut + baitCut + helperCut).coerceAtMost(MAX_WAIT_CUT_PERCENT)
    val seconds = rules.minWaitSeconds + random.nextFloat() * (rules.maxWaitSeconds - rules.minWaitSeconds)

    return seconds * (100 - cut) / 100f
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    private const val ROD_WEAR_PER_CAST = 1
    private const val NANOS_PER_MILLI = 1_000_000L

    /** However it is cut, a line waits a quarter of its time at least: the cap every tool's time cut shares. */
    private const val MAX_WAIT_CUT_PERCENT = 75

    /** A rod asks the quiver for bait as a bow asks it for arrows; it shoots nothing, so it has no range. */
    val BAIT_LINE = RangedWeapon(range = 0, ammoType = AmmoType.BAIT)

    /** The gathering row of the experience table on the design docs' bestia page. */
    private fun gatheringExpOf(catchLevel: Int): Int {
      return 2 * catchLevel + 5
    }
  }
}
