package net.bestia.zone.ecs.spawn

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Hands every rehydrated creature back to the den that made it.
 *
 * ### The problem this exists for
 *
 * Two boot passes each do half a job and neither can do the other's. `WildSpawnerBootRunner` rebuilds the
 * dens from the generator's markers, giving each a fresh entity id and an **empty** `spawnedEntities`.
 * `MobEntityPersister.loadAll` rehydrates the creatures, which arrive carrying a [DenMember] naming a den by
 * its durable [DenIdentity] but attached to nothing. Left there, the creatures belong to no den - never
 * counted against its pack size, never despawned when it goes dormant - while the den, believing itself
 * empty, spawns a full pack on top. That is the population inflating on every single restart.
 *
 * ### Why the lookup is derived rather than registered
 *
 * `SpawnerCellIndex` is the precedent for an index written by `WildSpawnerBootRunner`, but it exists because
 * a *per-tick* query needs one. This lookup is used once, at boot, and a second registration call in the
 * boot runner is a second thing to forget - with the failure mode of forgetting it being a den that exists
 * but cannot be found, which is precisely the silent half-working this area is prone to. Walking the
 * `Spawner` store instead means the lookup **cannot** disagree with the dens that actually exist. One
 * O(dens) pass at boot buys that.
 *
 * ### Why the match is full identity equality
 *
 * A feature id alone would be a key that looks stable and is not: it is a hash of a sequential ordinal, so
 * retuning `SpawnerParams.candidateSpacing` renames every den on the world and hands each old name to some
 * unrelated new den. Comparing the whole [DenIdentity] - which the den standing in the world is, by
 * construction, stamped with for *this* world - makes adoption-by-the-wrong-den structurally impossible
 * rather than merely guarded against. Hence also no `WorldService` dependency here: the dens are the answer.
 *
 * Idempotent. `spawnedEntities` is a set and a second run finds nothing to discard, so calling this twice is
 * harmless rather than destructive.
 */
@Service
class DenPackRestoreService(
  private val spawnerSystem: SpawnerSystem,
  private val deletionQueue: PersistedEntityDeletionQueue,
) {

  /**
   * @property reattached creatures handed back to their den
   * @property adoptedDens dens whose restored pack [SpawnerSystem] has taken responsibility for
   * @property discarded creatures whose den this world no longer has
   * @property trimmed creatures beyond their den's current pack size
   * @property unowned creatures with no den at all - `/spawn`ed, or rows predating den ownership
   */
  data class Result(
    val reattached: Int = 0,
    val adoptedDens: Int = 0,
    val discarded: Int = 0,
    val trimmed: Int = 0,
    val unowned: Int = 0,
  )

  fun restore(world: World): Result {
    val members = mutableListOf<Pair<EntityId, DenIdentity>>()
    world.each(DenMember::class) { id, member -> members.add(id to member.den) }

    val unowned = countUnownedMobs(world)
    if (members.isEmpty()) {
      return Result(unowned = unowned)
    }

    val dens = densByFeatureId(world)

    val packs = HashMap<EntityId, MutableList<EntityId>>()
    val orphans = mutableListOf<EntityId>()

    var mismatchedWorld = 0
    var mismatchedVersion = 0

    for ((creatureId, den) in members) {
      val found = dens[den.featureId]
      if (found == null) {
        orphans.add(creatureId)
        continue
      }

      val (denId, spawner) = found
      if (spawner.identity != den) {
        // Reported apart so a reseeded world reads differently from a retuned one in the log - the two
        // have very different causes and only one of them is anybody's mistake.
        if (spawner.identity.worldId != den.worldId) mismatchedWorld++ else mismatchedVersion++
        orphans.add(creatureId)
        continue
      }

      packs.getOrPut(denId) { mutableListOf() }.add(creatureId)
    }

    var reattached = 0
    var trimmed = 0

    for ((denId, pack) in packs) {
      val spawner = world.get(denId, Spawner::class) ?: continue

      // A den's pack size can shrink between boots - `wild-spawn` band multipliers and the generator's own
      // params both move it. Without this the den would sit permanently over its size, and the surplus
      // would only ever be cleared by the den going dormant.
      val keep = pack.take(spawner.maxSpawnCount)
      val surplus = pack.drop(spawner.maxSpawnCount)

      spawner.spawnedEntities.addAll(keep)
      reattached += keep.size

      for (creatureId in surplus) {
        discard(world, creatureId)
        trimmed++
      }

      // After the trim, so the lifecycle never takes over a den that is already over its size.
      if (spawner.spawnedEntities.isNotEmpty()) {
        spawnerSystem.adoptRehydratedPack(denId)
      }
    }

    for (creatureId in orphans) {
      discard(world, creatureId)
    }

    if (orphans.isNotEmpty()) {
      LOG.warn {
        "${orphans.size} persisted creature(s) named a den this world no longer has and were discarded " +
            "($mismatchedWorld from a different world, $mismatchedVersion from a different pipeline " +
            "version, ${orphans.size - mismatchedWorld - mismatchedVersion} whose den is simply gone). " +
            "This is what a regenerated or retuned world looks like, not a fault."
      }
    }

    return Result(
      reattached = reattached,
      adoptedDens = packs.keys.count { world.get(it, Spawner::class)?.spawnedEntities?.isNotEmpty() == true },
      discarded = orphans.size,
      trimmed = trimmed,
      unowned = unowned,
    )
  }

  /**
   * Every den on the world, by its durable name.
   *
   * A duplicate is astronomically unlikely from a hash and would split one pack across two dens, so it is
   * worth a line rather than a silent `put`.
   */
  private fun densByFeatureId(world: World): Map<Long, Pair<EntityId, Spawner>> {
    val byFeatureId = HashMap<Long, Pair<EntityId, Spawner>>()
    world.each(Spawner::class) { id, spawner ->
      val previous = byFeatureId.put(spawner.identity.featureId, id to spawner)
      if (previous != null) {
        LOG.warn {
          "Two dens share feature id ${spawner.identity.featureId}; a restored pack would be split between " +
              "them. Keeping the last."
        }
      }
    }
    return byFeatureId
  }

  private fun discard(world: World, creatureId: EntityId) {
    deletionQueue.enqueue(creatureId)
    if (world.hasEntity(creatureId)) {
      world.destroy(creatureId)
    }
  }

  /**
   * Creatures that survived a restart owned by nothing.
   *
   * Counted and reported rather than cleaned up. Most are `/spawn`ed mobs, which are meant to persist; the
   * rest are rows written before den ownership existed. Both are immortal - nothing despawns a den-less mob
   * - but an unconditional wipe here would delete the former along with the latter, and the operator has a
   * documented world reset for that.
   */
  private fun countUnownedMobs(world: World): Int {
    var unowned = 0
    world.each(BestiaVisual::class) { id, _ ->
      // `!Account` is MobEntityPersister.supports' own test for "a world mob rather than a player bestia".
      if (!world.has(id, DenMember::class) && !world.has(id, Account::class)) {
        unowned++
      }
    }
    return unowned
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
