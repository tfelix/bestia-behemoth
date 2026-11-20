package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.ecs.Empyrite
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import org.springframework.stereotype.Service

/**
 * The standing Empyrites, as entities. Not stored as entities: every boot raises them again from the Strongholds,
 * with the health last written. Tick thread only after boot.
 */
@Service
class Empyrites(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val sprouts: EmpyriteSeedSprouts,
  private val spots: SeedSpots,
  private val config: StrongholdConfig,
) {

  /** Boot only, after the Strongholds are restored. */
  fun raiseAll(world: World) {
    strongholds.all().filter { it.state == State.STANDING }.forEach { raise(world, it) }
  }

  /** The guild's site is done: the Empyrite stands, whole. */
  fun stand(world: World, site: Stronghold) {
    val standing = site.copy(state = State.STANDING, siteStructureId = null, empyriteHp = config.empyriteHp)
    strongholds.put(standing)
    raise(world, standing)

    LOG.info { "The Empyrite of guild ${standing.guildId} stands at seed ${standing.rank}" }
  }

  fun recordHp(rank: Int, hp: Int) {
    val stronghold = strongholds.on(rank) ?: return
    if (stronghold.empyriteHp != hp) strongholds.put(stronghold.copy(empyriteHp = hp))
  }

  /** The Empyrite broke. The guild loses the Stronghold, and the seed sprouts again for whoever plants next. */
  fun broken(world: World, rank: Int) {
    val stronghold = strongholds.on(rank) ?: return
    strongholds.remove(rank)
    seeds.awake().firstOrNull { it.rank == rank }?.let { sprouts.raise(world, it) }

    LOG.info { "The Empyrite of guild ${stronghold.guildId} at seed $rank broke" }
  }

  private fun raise(world: World, stronghold: Stronghold) {
    val seed = seeds.awake().firstOrNull { it.rank == stronghold.rank } ?: return
    val hp = if (stronghold.empyriteHp > 0) stronghold.empyriteHp.coerceAtMost(config.empyriteHp) else config.empyriteHp

    world.createEntity { id ->
      add(id, Position.fromVec3(spots.standingAt(seed)))
      add(id, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.EMPYRITE.ordinal.toLong()))
      add(id, Health(current = hp, max = config.empyriteHp))
      add(id, CombatStats(CombatValues.ofStructure(config.empyriteDefense)))
      add(id, GuildOwned(stronghold.guildId))
      add(id, Empyrite(stronghold.rank))
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
