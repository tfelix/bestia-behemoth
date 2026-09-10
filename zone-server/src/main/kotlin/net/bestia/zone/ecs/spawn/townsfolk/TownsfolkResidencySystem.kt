package net.bestia.zone.ecs.spawn.townsfolk

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.ecs.AiThrottleable
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.battle.status.Invulnerable
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Puts a town's people on the ground while a player is near them, and takes them away again.
 *
 * `AmbientSpawnerSystem`'s shape, beside it in the order, and for the same reason: a population that only
 * exists where somebody can see it is the only way a world of three hundred settlements is affordable.
 *
 * ### Activation is on where somebody would be, not where they live
 *
 * The difference from the ambient layer, and it is forced. An ambient site is a point; an inhabitant is a
 * house *and* a post, and a farmer's field can be a kilometre from their door. A ring around the house
 * wide enough to also cover the field would populate half a city to show one man hoeing, and a ring
 * around the field alone would empty the streets. So each person is tested at their **anchor** - where
 * the clock says they would be - which is a pure function of their occupation and the hour and costs
 * nothing to ask. See [TownsfolkRoster.Resident.anchorAt].
 *
 * ### Two maps, not three
 *
 * `AmbientSpawnerSystem` keeps a third for cells whose creature was killed, so a site stays empty for the
 * respawn delay. Townsfolk are `Invulnerable`, so nothing is ever killed and there is nothing to hold a
 * delay against. An entity that vanishes has gone through a door, and [IndoorRegistry] is what decides
 * when it comes back.
 */
@SpringComponent
@Order(82)
class TownsfolkResidencySystem(
  private val roster: TownsfolkRoster,
  private val sites: SettlementSiteIndex,
  private val indoors: IndoorRegistry,
  private val spawner: TownsfolkEntitySpawner,
  private val clock: BestiaClock,
  private val config: TownsfolkResidencyConfig,
) : System {

  /** Matches the ambient layer: activation is a coarse gate and a quarter second is inside the margin. */
  override val schedule: Schedule = Schedule.EverySeconds(0.25f)

  override val reads: ComponentClassSet = setOf(ActivePlayer::class, Position::class)

  /** The markers put on somebody arriving, for `AmbientSpawnerSystem`'s reason - see its own note. */
  override val writes: ComponentClassSet = setOf(Townsfolk::class, AiThrottleable::class, Invulnerable::class)

  /** Who is standing about, by identity. The single record of what this system has put down. */
  private val live = HashMap<Long, EntityId>()

  /** People nobody is near any more, and the [elapsed] stamp at which that became true. */
  private val idleSince = HashMap<Long, Float>()

  /** Simulated seconds since boot, for `Schedule.EverySeconds`' own determinism under test. */
  private var elapsed = 0f

  override fun update(world: World, deltaTime: Float) {
    if (!config.enabled) return

    elapsed += deltaTime

    // Anybody whose entity is gone walked through a door. Dropping them here rather than treating it as a
    // death is the whole difference from the ambient layer: the registry already knows when they are due
    // back, and re-materialising them now would put them straight back on the doorstep they just left.
    live.entries.removeIf { !world.hasEntity(it.value) }

    val players = activePlayerPositions(world)
    if (players.isEmpty()) {
      expireIdle(world)
      return
    }

    val wanted = wantedNear(players)

    stock(world, wanted)

    // `putIfAbsent`, so the stamp records when somebody *became* unwatched rather than the last time we
    // noticed they still were - otherwise the delay would never expire.
    for (identity in live.keys) {
      if (identity !in wanted) idleSince.putIfAbsent(identity, elapsed)
    }

    expireIdle(world)
  }

  /** Everybody whose anchor is inside some player's ring, and who is not already behind a door. */
  private fun wantedNear(players: List<Vec3L>): Map<Long, Vec3L> {
    val hour = clock.now().hour
    val radius = config.activationRadiusTiles
    val wanted = HashMap<Long, Vec3L>()

    for (settlement in settlementsNear(players, radius)) {
      for (resident in roster.of(settlement)) {
        if (indoors.isIndoors(resident.identity)) continue

        val anchor = resident.anchorAt(hour)
        if (players.any { within(anchor, it, radius) }) wanted[resident.identity] = anchor
      }
    }

    return wanted
  }

  /**
   * The settlements close enough that somebody in them might be in range.
   *
   * The coarse half of the same two-phase shape `TownClearance` uses: almost every player is nowhere near
   * a town, and a disc test against the settlement index answers those without expanding a roster.
   */
  private fun settlementsNear(players: List<Vec3L>, radius: Long): Set<Int> {
    val found = LinkedHashSet<Int>()
    for (player in players) {
      sites.coveringWithin(player.x, player.y, radius).forEach { found.add(it) }
    }
    return found
  }

  private fun stock(world: World, wanted: Map<Long, Vec3L>) {
    var budget = config.spawnsPerPass

    for ((identity, anchor) in wanted) {
      idleSince.remove(identity)
      if (identity in live) continue
      if (budget == 0) break

      val id = spawner.emerge(world, identity, anchor) ?: continue
      live[identity] = id
      budget--
    }
  }

  private fun expireIdle(world: World) {
    val iterator = idleSince.entries.iterator()
    while (iterator.hasNext()) {
      val (identity, since) = iterator.next()
      if (elapsed - since < config.unloadDelaySeconds) continue

      iterator.remove()
      val id = live.remove(identity) ?: continue

      // No deletion queued: a townsperson was never persisted, and nothing about them is written down.
      if (world.hasEntity(id)) world.destroy(id)
      LOG.trace { "Unloaded ${TownsfolkIdentity.describe(identity)}" }
    }
  }

  /** Horizontal only, as every activation ring in this server is: height is the wrong axis for visibility. */
  private fun within(at: Vec3L, player: Vec3L, radius: Long): Boolean {
    val dx = at.x - player.x
    val dy = at.y - player.y
    return dx * dx + dy * dy <= radius * radius
  }

  /** The same anchor set the other spawners use: only a player who has picked a master. */
  private fun activePlayerPositions(world: World): List<Vec3L> {
    val positions = ArrayList<Vec3L>()
    world.query(Position::class, ActivePlayer::class).each {
      positions.add(get<Position>().toVec3L())
    }
    return positions
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
