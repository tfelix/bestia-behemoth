package net.bestia.zone.ecs.spawn.ambient

import net.bestia.zone.ai.ecs.AiThrottleable
import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps the country around each player populated, and lets the rest of the world stay a formula.
 *
 * ### The same three phases as a den, for the same reason
 *
 * `SpawnerSystem`'s broad/narrow/diff shape, and the resemblance is deliberate. What differs is that there
 * is nothing to find: a den is an entity that has to be discovered near a player, while an ambient site is
 * *computed* from the player's position, so the broad phase is arithmetic over lattice cells rather than a
 * spatial index probe. The rest - a hysteresis delay so pacing a boundary does not churn, a `live` map as
 * the single record of what is stocked - is the same, and for the reasons that file gives.
 *
 * ### Why the budget is global
 *
 * A den restocks one creature per pass because its pack is small and its own. Here a player arriving in
 * fresh country has on the order of a hundred and forty empty sites at once, so the budget belongs to the
 * system rather than to a site. [AmbientSpawnConfig.activationRadiusTiles] is what hides the resulting few
 * seconds of filling: it reaches beyond the interest range, so the creatures a player can actually see were
 * placed before they came into view.
 *
 * ### What this must never touch
 *
 * A creature without an [Ambient] marker. Tearing one down would destroy a den's pack or a player's bestia,
 * and the marker is checked rather than the position for exactly that reason.
 *
 * Ambient creatures are also **not** persisted, so teardown does not enqueue a deletion - there is no row.
 * `AmbientSpawnerSystemTest` asserts that absence, because it is the one line where this and
 * `SpawnerSystem.despawnPack` genuinely differ and a later reader would otherwise "fix" it.
 */
@SpringComponent
@Order(81)
class AmbientSpawnerSystem(
  private val bestiaEntitySpawner: BestiaEntitySpawner,
  private val resolver: AmbientSiteResolver,
  private val config: AmbientSpawnConfig
) : System {

  /** Matches `SpawnerSystem`: activation is a coarse gate and a quarter second disappears into the margin. */
  override val schedule: Schedule = Schedule.EverySeconds(0.25f)

  override val reads: ComponentClassSet = setOf(ActivePlayer::class, Position::class)

  /**
   * Both markers this system puts on a creature, not just its own.
   *
   * `AiThrottleable` is declared because the AI stages *read* it, and `SystemScheduler` decides what may
   * share a wave from these sets alone - a system that quietly writes a component it did not declare appears
   * to conflict with nobody. The components `BestiaEntitySpawner` puts on a brand-new entity are not listed:
   * no other system can hold an entity that did not exist when the wave began.
   */
  override val writes: ComponentClassSet = setOf(Ambient::class, AiThrottleable::class)

  /** Cell to the creature standing in it. The single record of what this system has stocked. */
  private val live = HashMap<Long, EntityId>()

  /** Stocked cells with no player near them, and the [elapsed] stamp at which that became true. */
  private val idleSince = HashMap<Long, Float>()

  /**
   * Cells whose creature died, and when. Keeps a killed site empty for the respawn delay.
   *
   * Unlike [live] and [idleSince], nothing about a player's position bounds this - see the prune in [update].
   */
  private val emptiedAt = HashMap<Long, Float>()

  /** Simulated seconds since boot, for `Schedule.EverySeconds`' own determinism under test. */
  private var elapsed = 0f

  override fun update(world: World, deltaTime: Float) {
    if (!config.enabled) return

    elapsed += deltaTime

    // Before anything else, because the other maps are bounded by what is near a player and this one is not:
    // a cell is stamped when its creature dies and cleared when the site restocks, so a player who kills
    // something and walks away leaves an entry no other path can reach. Pruning at the same threshold `stock`
    // compares against is behaviour-preserving - an expired stamp already means "restock freely".
    emptiedAt.entries.removeIf { elapsed - it.value >= config.respawnDelaySeconds }

    val players = activePlayerPositions(world)
    if (players.isEmpty()) {
      expireIdle(world)
      return
    }

    val wanted = HashSet<Long>()
    for (player in players) {
      collectSitesNear(player, wanted)
    }

    reapDead(world, wanted)
    stock(world, wanted)

    // `putIfAbsent`, so the stamp records when a cell *became* idle rather than the last time we noticed it
    // still was - otherwise the delay would never expire.
    for (cell in live.keys) {
      if (cell !in wanted) idleSince.putIfAbsent(cell, elapsed)
    }

    expireIdle(world)
  }

  /**
   * The cells whose site lies within the activation radius of this player.
   *
   * Horizontal distance only, as `SpawnerSystem.withinActivation` is and for its reason: height is the wrong
   * axis for "can this be seen".
   */
  private fun collectSitesNear(player: Vec3L, into: MutableSet<Long>) {
    val radius = config.activationRadiusTiles
    resolver.lattice.forEachCellNear(player.x, player.y, radius) { cellX, cellY ->
      val site = resolver.siteAt(cellX, cellY) ?: return@forEachCellNear
      val dx = site.position.x - player.x
      val dy = site.position.y - player.y
      if (dx * dx + dy * dy <= radius * radius) into.add(site.cell)
    }
  }

  /**
   * Drops cells whose creature is gone, so the site is not counted as stocked and does not restock at once.
   *
   * Only for cells still wanted: one that is out of range goes down the ordinary idle path, and stamping it
   * as recently emptied would hold a respawn delay against ground nobody is near.
   */
  private fun reapDead(world: World, wanted: Set<Long>) {
    val gone = live.entries.filter { !world.hasEntity(it.value) }
    for ((cell, _) in gone) {
      live.remove(cell)
      idleSince.remove(cell)
      if (cell in wanted) emptiedAt[cell] = elapsed
    }
  }

  private fun stock(world: World, wanted: Set<Long>) {
    var budget = config.spawnsPerPass

    for (cell in wanted) {
      if (budget == 0) break

      idleSince.remove(cell)
      if (cell in live) continue

      val emptied = emptiedAt[cell]
      if (emptied != null) {
        if (elapsed - emptied < config.respawnDelaySeconds) continue
        emptiedAt.remove(cell)
      }

      val site = resolver.siteAt(
        AmbientSiteLattice.unpackX(cell),
        AmbientSiteLattice.unpackY(cell)
      ) ?: continue

      live[cell] = spawn(world, site)
      budget--
    }
  }

  private fun spawn(world: World, site: AmbientSite): EntityId {
    // `persistent = false` is the whole reason this layer can exist at this density: a hundred and forty
    // creatures per player, none of them worth a database row or a place in the 90-second persistence sweep.
    val id = bestiaEntitySpawner.spawnMob(
      world,
      site.bestiaId,
      site.position,
      persistent = false
    )

    // Applied at the end of the tick, like every structural change from inside a system - `World.tick` holds
    // `iterating` for the whole scheduler pass. Harmless for both of these: nothing reads them in the tick a
    // creature is born, teardown is driven by `live` rather than by the marker, and an agent has not
    // perceived anything yet so a tick at full cadence costs nothing. This is why `spawnMob` takes `den` as
    // a parameter instead - that one has to be on before the persistence sweep can see the entity.
    world.add(id, Ambient(site.cell))
    if (site.throttleable) world.add(id, AiThrottleable)

    return id
  }

  private fun expireIdle(world: World) {
    val iterator = idleSince.entries.iterator()
    while (iterator.hasNext()) {
      val (cell, since) = iterator.next()
      if (elapsed - since < config.unloadDelaySeconds) continue

      iterator.remove()
      emptiedAt.remove(cell)
      val id = live.remove(cell) ?: continue

      // No deletion-queue entry: an ambient creature was never persisted. See the class KDoc.
      if (world.hasEntity(id)) world.destroy(id)
    }
  }

  private fun activePlayerPositions(world: World): List<Vec3L> {
    val positions = ArrayList<Vec3L>()
    world.query(Position::class, ActivePlayer::class).each {
      positions.add(get<Position>().toVec3L())
    }
    return positions
  }

  /** Creatures this system currently keeps alive, for the debug command. */
  val liveCount: Int get() = live.size

  /** Sites waiting out their respawn delay. Exposed so a test can prove the map stays bounded. */
  internal val pendingRespawnCount: Int get() = emptiedAt.size
}
