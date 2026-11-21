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
  private val stewards: Stewards,
  private val watchers: Watchers,
  private val config: StrongholdConfig,
) {

  /** Boot only, after the Strongholds are restored. */
  fun raiseAll(world: World) {
    strongholds.all().filter { it.state == State.STANDING }.forEach { raise(world, it) }
  }

  /** The guild's site is done: the Empyrite stands, whole. */
  fun stand(world: World, site: Stronghold) {
    val standing = site.copy(state = State.STANDING, siteStructureId = null, empyriteHp = maxHpOf(site))
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
   * once, whole, and nothing harms it for a while. Without such a guild, the seed is free again. Either way the
   * steward and the watchers leave; a new owner hires and calls its own.
   */
  fun broken(world: World, rank: Int, breakerGuildId: Long?) {
    val stronghold = strongholds.on(rank) ?: return
    stewards.dismiss(world, rank)
    watchers.dismissAll(world, rank)

    if (breakerGuildId == null || breakerGuildId == stronghold.guildId) {
      strongholds.remove(rank)
      seeds.awake().firstOrNull { it.rank == rank }?.let { sprouts.raise(world, it) }
      LOG.info { "The Empyrite of guild ${stronghold.guildId} at seed $rank broke, and nobody took it" }
      return
    }

    val weakened = stronghold.copy(tracks = stronghold.tracks.mapValues { (_, progress) -> progress.lostLevel() })
    val captured = weakened.copy(guildId = breakerGuildId, empyriteHp = maxHpOf(weakened), hasSteward = false, watchers = emptyMap(), watcherStrength = 0)
    strongholds.put(captured)
    val empyriteId = raise(world, captured) ?: return
    val graceSeconds = config.captureGraceMinutes * 60.0
    if (graceSeconds > 0) {
      statusEffects.applyEffect(
        world, empyriteId, StatusEffectId.INVULNERABLE, level = 1, durationSeconds = graceSeconds
      )
    }

    LOG.info { "Guild $breakerGuildId captured the Stronghold of guild ${stronghold.guildId} at seed $rank" }
  }

  /** A new Defense level hardens a standing Empyrite. Damage taken stays taken: it gains the new health, no heal. */
  fun harden(world: World, rank: Int) {
    val stronghold = strongholds.on(rank)?.takeIf { it.state == State.STANDING } ?: return
    var empyriteId: EntityId? = null
    world.query(Empyrite::class).each { id -> if (get<Empyrite>().rank == rank) empyriteId = id }
    val health = empyriteId?.let { world.get(it, Health::class) } ?: return

    val missing = health.max - health.current
    health.max = maxHpOf(stronghold)
    health.current = (health.max - missing).coerceAtLeast(1)
    world.get(empyriteId!!, CombatStats::class)?.values = CombatValues.ofStructure(defenseOf(stronghold))
  }

  fun maxHpOf(stronghold: Stronghold): Int {
    return config.empyriteHp + config.empyriteHpPerDefense * stronghold.progressOf(StrongholdTrack.DEFENSE).level
  }

  private fun defenseOf(stronghold: Stronghold): Int {
    val level = stronghold.progressOf(StrongholdTrack.DEFENSE).level

    return config.empyriteDefense + config.empyriteDefensePerLevel * level
  }

  private fun raise(world: World, stronghold: Stronghold): EntityId? {
    val seed = seeds.awake().firstOrNull { it.rank == stronghold.rank } ?: return null
    val maxHp = maxHpOf(stronghold)
    val hp = if (stronghold.empyriteHp > 0) stronghold.empyriteHp.coerceAtMost(maxHp) else maxHp

    return world.createEntity { id ->
      add(id, Position.fromVec3(spots.standingAt(seed)))
      add(id, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.EMPYRITE.ordinal.toLong()))
      add(id, Health(current = hp, max = maxHp))
      add(id, CombatStats(CombatValues.ofStructure(defenseOf(stronghold))))
      add(id, GuildOwned(stronghold.guildId))
      add(id, Empyrite(stronghold.rank))
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
