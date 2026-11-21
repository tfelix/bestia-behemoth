package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.respawn.GuildRespawnPoints
import net.bestia.zone.script.ecs.ScriptComponent
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.stronghold.ecs.Steward
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * The stewards standing beside their Empyrites, where members open the guild storage. Like the Empyrites, not stored as entities: every boot raises them
 * again from the Strongholds that hired one. The species is a non-combatant, so nothing harms a steward.
 *
 * A guild's fallen masters get up at the nearest of its Strongholds that has a steward.
 * Tick thread only after boot.
 */
@Service
class Stewards(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val spots: SeedSpots,
  private val bestias: BestiaCatalogue,
  private val spawner: BestiaEntitySpawner,
) : GuildRespawnPoints {

  /** Boot only, after the Strongholds are restored. */
  fun raiseAll(world: World) {
    strongholds.all().filter { it.state == State.STANDING && it.hasSteward }.forEach { raise(world, it) }
  }

  fun hire(world: World, stronghold: Stronghold) {
    val hired = stronghold.copy(hasSteward = true)
    strongholds.put(hired)
    raise(world, hired)

    LOG.info { "Guild ${hired.guildId} hired a steward at seed ${hired.rank}" }
  }

  /** The steward leaves with the contract of the guild that hired it, when the Stronghold falls or is taken. */
  fun dismiss(world: World, rank: Int) {
    val leaving = ArrayList<EntityId>()
    world.query(Steward::class).each { id -> if (get<Steward>().rank == rank) leaving.add(id) }
    leaving.forEach { world.destroy(it) }
  }

  override fun respawnPointFor(world: World, masterEntityId: EntityId): Vec3L? {
    val guildId = world.get(masterEntityId, GuildMembership::class)?.guildId ?: return null
    val fellAt = world.get(masterEntityId, Position::class)?.toVec3L() ?: return null

    return strongholds.heldBy(guildId)
      .filter { it.state == State.STANDING && it.hasSteward }
      .mapNotNull { returnPointOf(it) }
      .minByOrNull { it.distance(fellAt) }
  }

  /** In front of the steward. */
  private fun returnPointOf(stronghold: Stronghold): Vec3L? {
    val seed = seeds.awake().firstOrNull { it.rank == stronghold.rank } ?: return null

    return spots.standingAt(seed, STEWARD_OFFSET_METRES, -RETURN_OFFSET_METRES)
  }

  private fun raise(world: World, stronghold: Stronghold) {
    val seed = seeds.awake().firstOrNull { it.rank == stronghold.rank } ?: return
    val at = spots.standingAt(seed, STEWARD_OFFSET_METRES, 0.0)

    val id = spawner.spawnMob(world, bestias.byIdentifier(SPECIES).id, at, persistent = false)
    world.add(id, Steward(stronghold.rank))
    world.add(id, ScriptComponent(checkNotNull(GuildSteward::class.simpleName)))
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val SPECIES = "guild_steward"

    /** Beside the Empyrite rather than in it, so both can be clicked. */
    const val STEWARD_OFFSET_METRES = 3.0
    const val RETURN_OFFSET_METRES = 2.0
  }
}
