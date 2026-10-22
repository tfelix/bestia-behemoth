package net.bestia.zone.guild.ecs

import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.PlaceNameService
import net.bestia.zone.util.AccountId
import net.bestia.zone.weather.EnvironmentalExposureSystem
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps each played guild member's [GuildMemberBeacon] current: where the entity its account plays stands, and
 * how its master fares.
 *
 * A beacon is only sent when it changes, so a member coming online would not hear of members standing still.
 * Whenever the played members of a guild change, every beacon of that guild is therefore sent again.
 */
@SpringComponent
class GuildMemberBeaconSystem(
  private val placeNames: PlaceNameService,
) : System {
  override val phase = Phase.UPKEEP

  /** Exposure takes its health in the same phase; the beacon reports what is left. */
  override val after = setOf(EnvironmentalExposureSystem::class)

  override val schedule: Schedule = Schedule.EverySeconds(1f)

  override val reads: ComponentClassSet = setOf(
    GuildMembership::class, Account::class, ActivePlayer::class, Position::class, Health::class, Mana::class,
  )

  override val writes: ComponentClassSet = setOf(GuildMemberBeacon::class)

  /** By guild id, as of the last run. Tick thread only. */
  private var playedByGuild: Map<Long, Set<AccountId>> = emptyMap()

  override fun update(world: World, deltaTime: Float) {
    val playedPositions = positionsOfPlayedEntities(world)
    val guildsWithNewcomers = updatePlayedByGuild(world, playedPositions.keys)

    world.query(GuildMemberBeacon::class, GuildMembership::class, Account::class, Health::class, Mana::class).each {
      val position = playedPositions[get<Account>().accountId] ?: return@each
      val health = get<Health>()
      val mana = get<Mana>()
      val beacon = get<GuildMemberBeacon>()

      val vitals = GuildMemberBeacon.Vitals(health.current, health.max, mana.current, mana.max)
      beacon.update(position, vitals) { placeNames.resolve(it.x, it.y).name }

      if (get<GuildMembership>().guildId in guildsWithNewcomers) {
        beacon.markDirty()
      }
    }
  }

  /** The account's master, or the bestia it controls instead. */
  private fun positionsOfPlayedEntities(world: World): Map<AccountId, Vec3L> {
    val positions = HashMap<AccountId, Vec3L>()
    world.query(ActivePlayer::class, Account::class, Position::class).each {
      positions[get<Account>().accountId] = get<Position>().toVec3L()
    }

    return positions
  }

  /** @return the guilds where somebody started being played since the last run */
  private fun updatePlayedByGuild(world: World, playedAccounts: Set<AccountId>): Set<Long> {
    val playedNow = HashMap<Long, MutableSet<AccountId>>()
    world.query(GuildMembership::class, Account::class).each {
      val accountId = get<Account>().accountId
      if (accountId in playedAccounts) {
        playedNow.getOrPut(get<GuildMembership>().guildId) { HashSet() }.add(accountId)
      }
    }

    val withNewcomers = playedNow.filterTo(HashMap()) { (guildId, played) ->
      !playedByGuild[guildId].orEmpty().containsAll(played)
    }.keys
    playedByGuild = playedNow

    return withNewcomers
  }
}
