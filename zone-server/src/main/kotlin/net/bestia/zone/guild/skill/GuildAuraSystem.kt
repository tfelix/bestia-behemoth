package net.bestia.zone.guild.skill

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.guild.GuildConfig
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps [GuildAura] on everything guild members have in the world - their masters and their bestias - while it
 * stands within [GuildConfig.auraRadiusMetres] of what the guild leader plays.
 *
 * Walks the players' entities rather than asking the spatial index: a few hundred entities, against one query
 * per leader online.
 */
@SpringComponent
class GuildAuraSystem(
  private val statusEffectService: StatusEffectService,
  private val config: GuildConfig,
) : System {

  override val phase = Phase.STATUS
  override val schedule: Schedule = Schedule.EverySeconds(PULSE_SECONDS)
  override val after = setOf(StatusEffectDurationSystem::class)
  override val before = setOf(StatusValueRecalcSystem::class)

  override val reads: ComponentClassSet = setOf(
    GuildMembership::class, Master::class, Account::class, ActivePlayer::class, OwnedBestia::class,
    Position::class,
  )
  override val writes: ComponentClassSet = setOf(StatusEffects::class, IsStatusValueDirty::class)

  /** Per guild, the aura levels its members were last given, so a new level reaches them before a refresh. */
  private val lastLevels = mutableMapOf<Long, Map<GuildAuraSkill, Int>>()

  private class Leader(val entityId: EntityId, val accountId: Long, val levels: Map<GuildAuraSkill, Int>)

  override fun update(world: World, deltaTime: Float) {
    val leaders = leadersWithAuras(world)
    if (leaders.isEmpty()) {
      lastLevels.clear()
      return
    }

    val guildOfMaster = guildOfEveryMaster(world)
    val centres = leaders.mapValues { (_, leader) -> playedPosition(world, leader) }

    world.query(Account::class, Position::class).each { id ->
      val masterId = world.get(id, Master::class)?.masterId ?: world.get(id, OwnedBestia::class)?.masterId
      val guildId = masterId?.let { guildOfMaster[it] } ?: return@each
      val leader = leaders[guildId] ?: return@each
      val centre = centres[guildId] ?: return@each

      if (isWithinRadius(centre, get<Position>())) {
        statusEffectService.applyEffect(world, id, StatusEffectId.GUILD_AURA, level = 1, leader.entityId)
        if (lastLevels[guildId] != leader.levels) {
          world.add(id, IsStatusValueDirty)
        }
      }
    }

    lastLevels.clear()
    leaders.forEach { (guildId, leader) -> lastLevels[guildId] = leader.levels }
  }

  private fun leadersWithAuras(world: World): Map<Long, Leader> {
    val leaders = mutableMapOf<Long, Leader>()

    world.query(GuildMembership::class, Account::class).each { id ->
      val membership = get<GuildMembership>()
      if (!membership.isLeader) {
        return@each
      }

      val levels = GuildAuraSkill.levelsOf(membership::skillLevel)
      if (levels.isNotEmpty()) {
        leaders[membership.guildId] = Leader(id, get<Account>().accountId, levels)
      }
    }

    return leaders
  }

  private fun guildOfEveryMaster(world: World): Map<Long, Long> {
    val guildOfMaster = mutableMapOf<Long, Long>()
    world.query(GuildMembership::class, Master::class).each { _ ->
      guildOfMaster[get<Master>().masterId] = get<GuildMembership>().guildId
    }

    return guildOfMaster
  }

  /** Where the leader is in the world: the bestia they play if they play one, else their master. */
  private fun playedPosition(world: World, leader: Leader): Position? {
    var played: Position? = null
    world.query(ActivePlayer::class, Account::class, Position::class).each { _ ->
      if (get<Account>().accountId == leader.accountId) {
        played = get<Position>()
      }
    }

    return played ?: world.get(leader.entityId, Position::class)
  }

  /** Height is ignored, as for a ward stone's field. */
  private fun isWithinRadius(centre: Position, at: Position): Boolean {
    val dx = at.x - centre.x
    val dy = at.y - centre.y
    val radius = config.auraRadiusMetres.toLong()

    return dx * dx + dy * dy <= radius * radius
  }

  private companion object {
    const val PULSE_SECONDS = 2f
  }
}
