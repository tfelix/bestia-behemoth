package net.bestia.zone.spawn.ecs

import net.bestia.zone.config.PartyRulesConfig
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Which party members count as near a kill, for sharing its EXP and taking turns at its loot: their master or
 * the entity they play stands within [PartyRulesConfig.expShareRangeMetres], flat on the map.
 *
 * Tick thread: reads the world and the session map, nothing else.
 */
@Component
class PartyMembersNearby(
  private val sessions: ConnectionInfoService,
  private val rules: PartyRulesConfig,
  private val masters: MasterEntityResolver = MasterEntityResolver(sessions),
) {

  val reads: ComponentClassSet = setOf(Position::class)

  /** The master entity of [member] when it is played near [at], else null. */
  fun masterNear(world: World, member: PartyMembership.Member, at: Vec3L): EntityId? {
    if (!sessions.isPlaying(member.accountId, member.masterId)) {
      return null
    }

    val memberMaster = masters.selectedMasterEntityOf(member.accountId) ?: return null
    val played = activeEntityOf(member.accountId) ?: memberMaster
    val near = isNear(world, memberMaster, at) || (played != memberMaster && isNear(world, played, at))

    return if (near) memberMaster else null
  }

  private fun isNear(world: World, entityId: EntityId, at: Vec3L): Boolean {
    val position = world.get(entityId, Position::class) ?: return false
    val dx = position.x - at.x
    val dy = position.y - at.y
    val range = rules.expShareRangeMetres.toLong()

    return dx * dx + dy * dy <= range * range
  }

  private fun activeEntityOf(accountId: AccountId): EntityId? {
    return try {
      sessions.getActiveEntityId(accountId)
    } catch (_: NoActiveSessionException) {
      null
    }
  }
}
