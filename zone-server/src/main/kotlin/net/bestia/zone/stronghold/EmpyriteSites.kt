package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/**
 * The Empyrite sites the guilds work on. A site is a player structure, so the construction slice stores and
 * restores it; this keeps it the guild's and lets it go when it is lost. Tick thread only.
 */
@Service
class EmpyriteSites(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val sprouts: EmpyriteSeedSprouts,
  private val structures: PlayerStructureRegistry,
  private val worldService: WorldService,
  private val news: StrongholdNews,
  private val config: StrongholdConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** Boot only, after the construction sites are back: a site's guild is not part of the structure row. */
  fun restoreOwners(world: World) {
    for (stronghold in strongholds.all().filter { it.state == State.SITE }) {
      val siteId = stronghold.siteStructureId?.let { siteEntityOf(world, it) } ?: continue
      world.add(siteId, GuildOwned(stronghold.guildId))
    }
  }

  /** A site that was destroyed, or that nobody worked on for too long, is gone, and its seed is free again. */
  fun expire(world: World) {
    val now = clock.instant()
    val idleLimit = Duration.ofHours(config.siteIdleHours)

    for (stronghold in strongholds.all().filter { it.state == State.SITE }.toList()) {
      val structure = stronghold.siteStructureId?.let(structures::of)
      val siteId = structure?.let { siteEntityOf(world, it.id) }
      val isWorked = siteId?.let { world.get(it, ConstructionSite::class)?.active } == true

      when {
        isWorked -> strongholds.put(stronghold.copy(siteWorkedAt = now))
        structure != null && stronghold.siteWorkedAt.plus(idleLimit) > now -> Unit
        else -> lose(world, stronghold, siteId, structure?.id)
      }
    }
  }

  private fun lose(world: World, stronghold: Stronghold, siteId: EntityId?, structureId: Long?) {
    siteId?.let(world::destroy)
    structureId?.let { id -> structures.of(id)?.let { removeStructure(it.id, it.position) } }
    strongholds.remove(stronghold.rank)
    seeds.awake().firstOrNull { it.rank == stronghold.rank }?.let { sprouts.raise(world, it) }
    news.lost(world, stronghold.guildId, stronghold.rank)

    LOG.info { "The Empyrite site of guild ${stronghold.guildId} at seed ${stronghold.rank} is gone" }
  }

  private fun removeStructure(structureId: Long, at: Vec3L) {
    val chunkSize = worldService.config.chunkSize.toLong()
    structures.remove(structureId, Math.floorDiv(at.x, chunkSize).toInt(), Math.floorDiv(at.y, chunkSize).toInt())
  }

  private fun siteEntityOf(world: World, structureId: Long): EntityId? {
    var found: EntityId? = null
    world.each(ConstructionSite::class) { id, site -> if (site.structureId == structureId) found = id }

    return found
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
