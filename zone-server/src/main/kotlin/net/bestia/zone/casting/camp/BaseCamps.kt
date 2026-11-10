package net.bestia.zone.casting.camp

import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.AreaEffectSpawner
import net.bestia.zone.battle.ecs.effects.AreaMechanics
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.prop.CampfireService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * The Base Camps standing in the world, one per master: a burning campfire made into a camp where the caster's
 * party bears the weather better, recovers faster at Lv.2 and respawns at Lv.3. The camp itself is an area that
 * keeps `CAMPED` on the party around the fire.
 */
@Service
class BaseCamps(
  private val campfires: CampfireService,
  private val areaEffects: AreaEffectSpawner,
  private val clock: Clock = Clock.systemUTC(),
) {

  private data class Camp(
    val effectId: EntityId,
    val position: Vec3L,
    val level: Int,
    val expiresAt: Instant,
  )

  private val byMasterId = ConcurrentHashMap<Long, Camp>()

  /**
   * Makes the burning campfire near [at] a camp of [casterId], if the fire is the caster's own or a party member's.
   * The caster's previous camp ends. False when there is no such fire.
   */
  fun pitch(world: World, casterId: EntityId, skillId: Long, at: Vec3L, level: Int, durationSeconds: Long): Boolean {
    val masterId = world.get(casterId, Master::class)?.masterId ?: return false
    val fire = campfires.burningNear(at, FIRE_REACH_TILES) ?: return false
    if (fire.ownerMasterId != masterId && fire.ownerMasterId !in partyOf(world, casterId)) return false

    byMasterId.remove(masterId)?.let { if (world.isAlive(it.effectId)) world.destroy(it.effectId) }

    val effect = AreaEffect(
      casterId = casterId,
      skillId = skillId,
      skillLevel = level,
      radiusTiles = RADIUS_TILES,
      damagePerTick = 0,
      tickIntervalSeconds = PULSE_SECONDS,
      remainingTicks = (durationSeconds / PULSE_SECONDS).toInt().coerceAtLeast(1),
      mechanics = AreaMechanics(zoneStatus = StatusEffectId.CAMPED, forAllies = true, partyOnly = true),
    )
    val effectId = areaEffects.spawn(world, fire.position, null, effect)
    byMasterId[masterId] = Camp(effectId, fire.position, level, clock.instant().plusSeconds(durationSeconds))

    return true
  }

  /** Where a dead master of [masterId]'s party respawns instead of the save point: a Lv.3 camp still standing. */
  fun respawnPointFor(world: World, masterEntityId: EntityId, masterId: Long): Vec3L? {
    val now = clock.instant()
    val party = partyOf(world, masterEntityId) + masterId

    return byMasterId.entries
      .firstOrNull { (campOwner, camp) -> campOwner in party && camp.level >= RESPAWN_LEVEL && camp.expiresAt.isAfter(now) }
      ?.value?.position
  }

  private fun partyOf(world: World, masterEntityId: EntityId): Set<Long> {
    return world.get(masterEntityId, PartyMembership::class)?.members?.map { it.masterId }?.toSet().orEmpty()
  }

  private companion object {
    const val FIRE_REACH_TILES = 2L

    /** About the 5 m the docs give, as a cube around the fire. */
    const val RADIUS_TILES = 5L
    const val PULSE_SECONDS = 2f
    const val RESPAWN_LEVEL = 3
  }
}
