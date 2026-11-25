package net.bestia.zone.master.bestia

import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.Healing
import net.bestia.zone.battle.Sides
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.mark.MarkedPreySystem
import net.bestia.zone.battle.status.EssenceAura
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.scripts.AuraScript
import net.bestia.zone.casting.detect.DetectingSystem
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * The boss essences that reach past their wearer: every second, each [EssenceAura] a master or Bestia wears acts on
 * those within its radius. Height is ignored, as for a song.
 */
@SpringComponent
class EssenceAuraSystem(
  private val statusEffects: StatusEffectService,
  private val aoi: EntityAOIService,
) : System {

  override val phase = Phase.STATUS
  override val schedule: Schedule = Schedule.EverySeconds(PULSE_SECONDS)
  override val after = setOf(StatusEffectDurationSystem::class)
  override val before = setOf(StatusValueRecalcSystem::class, DetectingSystem::class, MarkedPreySystem::class)

  override val reads: ComponentClassSet = setOf(
    CombatStats::class, Account::class, Position::class, Master::class, OwnedBestia::class, PartyMembership::class,
    Dead::class,
  ) + Sides.READS
  override val writes: ComponentClassSet = setOf(StatusEffects::class, IsStatusValueDirty::class, Health::class)

  private var pulses = 0L

  override fun update(world: World, deltaTime: Float) {
    pulses++

    for ((wearerId, auras) in wearers(world)) {
      val centre = world.get(wearerId, Position::class)?.toVec3L() ?: continue
      auras.forEach { aura -> pulse(world, wearerId, centre, aura) }
    }
  }

  private fun pulse(world: World, wearerId: EntityId, centre: Vec3L, aura: EssenceAura) {
    when (aura) {
      EssenceAura.ROYAL_SWARM -> friendsNear(world, wearerId, centre, aura, withParty = true).forEach {
        statusEffects.applyEffect(world, it, StatusEffectId.ROYAL_SWARM, level = 1, sourceEntityId = wearerId)
      }
      EssenceAura.CAPTAINS_ORDERS -> friendsNear(world, wearerId, centre, aura, withParty = false).forEach {
        statusEffects.applyEffect(world, it, StatusEffectId.CAPTAINS_ORDERS, level = 1, sourceEntityId = wearerId)
      }
      EssenceAura.BUDDING_BROOD -> if (pulses % BROOD_EVERY_PULSES == 0L) {
        friendsNear(world, wearerId, centre, aura, withParty = false).forEach { healBrood(world, it) }
      }
      EssenceAura.STENCH -> foesNear(world, wearerId, centre, aura).forEach {
        statusEffects.tryInflict(world, it, StatusEffectId.POISON, level = 1, wearerId, STENCH_CHANCE_PERCENT)
      }
    }
  }

  private fun wearers(world: World): List<Pair<EntityId, Set<EssenceAura>>> {
    val wearers = mutableListOf<Pair<EntityId, Set<EssenceAura>>>()
    world.query(Account::class, CombatStats::class).each { id ->
      val auras = get<CombatStats>().values.essenceAuras
      if (auras.isNotEmpty() && !world.has(id, Dead::class)) wearers.add(id to auras)
    }
    return wearers
  }

  /** The Bestias of the wearer's master, and with [withParty] the masters of its party, the wearer's own included. */
  private fun friendsNear(
    world: World,
    wearerId: EntityId,
    centre: Vec3L,
    aura: EssenceAura,
    withParty: Boolean,
  ): List<EntityId> {
    val ownerId = world.get(wearerId, Master::class)?.masterId ?: world.get(wearerId, OwnedBestia::class)?.masterId
      ?: return emptyList()
    val partyMasterIds = if (withParty) partyOf(world, ownerId) + ownerId else emptySet()

    val friends = mutableListOf<EntityId>()
    world.query(Account::class, Position::class).each { id ->
      if (!isWithin(centre, get<Position>().toVec3L(), aura.radiusMetres) || world.has(id, Dead::class)) return@each

      val ownBestia = world.get(id, OwnedBestia::class)?.masterId == ownerId
      val partyMaster = world.get(id, Master::class)?.masterId in partyMasterIds
      if (ownBestia || partyMaster) friends.add(id)
    }
    return friends
  }

  private fun partyOf(world: World, masterId: Long): Set<Long> {
    var members = emptySet<Long>()
    world.query(Master::class, PartyMembership::class).each {
      if (get<Master>().masterId == masterId) members = get<PartyMembership>().members.map { it.masterId }.toSet()
    }
    return members
  }

  private fun foesNear(world: World, wearerId: EntityId, centre: Vec3L, aura: EssenceAura): List<EntityId> {
    return aoi.queryEntitiesInCube(centre, aura.radiusMetres * 2, AoiLayer.DYNAMIC_ONLY).filter { id ->
      val at = world.get(id, Position::class)?.toVec3L() ?: return@filter false
      isWithin(centre, at, aura.radiusMetres) && !world.has(id, Dead::class) && world.has(id, Health::class) &&
        AuraScript.isFoeOf(world, wearerId, id)
    }
  }

  private fun healBrood(world: World, bestiaId: EntityId) {
    val max = world.get(bestiaId, Health::class)?.max ?: return
    Healing.heal(world, bestiaId, (max * BROOD_HEAL_PERCENT / 100).coerceAtLeast(1))
  }

  private fun isWithin(centre: Vec3L, at: Vec3L, radius: Long): Boolean {
    val dx = at.x - centre.x
    val dy = at.y - centre.y
    return dx * dx + dy * dy <= radius * radius
  }

  private companion object {
    const val PULSE_SECONDS = 1f
    const val BROOD_EVERY_PULSES = 3L
    const val BROOD_HEAL_PERCENT = 2

    /** Per second and foe, so a foe who stays close is poisoned within a few seconds. */
    const val STENCH_CHANCE_PERCENT = 10
  }
}
