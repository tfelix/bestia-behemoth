package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.ecs.Empyrite
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
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
  private val statusEffects: StatusEffectService,
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

  /**
   * The Empyrite broke. The guild whose member struck the blow takes the Stronghold: the Empyrite forms again at
   * once, whole, and nothing harms it for a while. Without such a guild, the seed is free again.
   */
  fun broken(world: World, rank: Int, breakerGuildId: Long?) {
    val stronghold = strongholds.on(rank) ?: return

    if (breakerGuildId == null || breakerGuildId == stronghold.guildId) {
      strongholds.remove(rank)
      seeds.awake().firstOrNull { it.rank == rank }?.let { sprouts.raise(world, it) }
      LOG.info { "The Empyrite of guild ${stronghold.guildId} at seed $rank broke, and nobody took it" }
      return
    }

    val captured = stronghold.copy(guildId = breakerGuildId, empyriteHp = config.empyriteHp)
    strongholds.put(captured)
    val empyriteId = raise(world, captured) ?: return
    val graceSeconds = config.captureGraceMinutes * 60.0
    if (graceSeconds > 0) {
      statusEffects.applyEffect(world, empyriteId, StatusEffectId.INVULNERABLE, level = 1, durationSeconds = graceSeconds)
    }

    LOG.info { "Guild $breakerGuildId captured the Stronghold of guild ${stronghold.guildId} at seed $rank" }
  }

  private fun raise(world: World, stronghold: Stronghold): EntityId? {
    val seed = seeds.awake().firstOrNull { it.rank == stronghold.rank } ?: return null
    val hp = if (stronghold.empyriteHp > 0) stronghold.empyriteHp.coerceAtMost(config.empyriteHp) else config.empyriteHp

    return world.createEntity { id ->
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
