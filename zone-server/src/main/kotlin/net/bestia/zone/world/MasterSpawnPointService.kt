package net.bestia.zone.world

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import net.bestia.zone.world.persistence.MasterSpawnPoint
import net.bestia.zone.world.persistence.MasterSpawnPointRepository

/**
 * Computes and caches the settlement-based spawn point candidates a new master can choose to start
 * near - see [SettlementSpawnPoints] for the selection itself. Computed once per world and cached in
 * [MasterSpawnPointRepository]; [WorldProvisioning.recreate] clears the cache whenever the world row
 * it belongs to is replaced, so the next call recomputes it for the new world.
 */
@Service
class MasterSpawnPointService(
  private val worldService: WorldService,
  private val repository: MasterSpawnPointRepository
) {

  /** Every spawn point of this world in the order homes are offered, computing them on first use. */
  @Transactional
  fun ensureComputed(): List<MasterSpawnPoint> {
    val existing = repository.findAll()
    if (existing.isNotEmpty()) return existing.sortedWith(compareBy({ it.rank }, { it.id }))

    val rows = rowsFor(SettlementSpawnPoints.choose(worldService.generated, HOMES_HELD), firstRank = 0)

    if (rows.isEmpty()) {
      LOG.warn { "No settlement spawn point candidates were computed for world '${worldService.record.name}'" }
      return rows
    }

    val saved = repository.saveAll(rows)
    LOG.info {
      "Computed ${saved.size} master spawn point candidate(s): ${saved.joinToString { it.settlementName }}"
    }
    return saved
  }

  /**
   * Appends the homes a world computed before it held [HOMES_HELD] of them, behind the ones it has. Once per boot:
   * choosing runs a land search per town.
   */
  @Transactional
  fun ensureReserves() {
    val existing = repository.findAll()
    if (existing.isEmpty() || existing.size >= HOMES_HELD) return

    val known = existing.map { it.settlementIndex }.toSet()
    val missing = SettlementSpawnPoints.choose(worldService.generated, HOMES_HELD)
      .filter { it.settlementIndex !in known }
    if (missing.isEmpty()) return

    repository.saveAll(rowsFor(missing, firstRank = existing.maxOf { it.rank } + 1))
    LOG.info { "Added ${missing.size} reserve home(s): ${missing.joinToString { it.name }}" }
  }

  private fun rowsFor(candidates: List<SettlementSpawnPoints.Candidate>, firstRank: Int): List<MasterSpawnPoint> {
    val generated = worldService.generated
    val config = generated.config

    return candidates.mapIndexed { offset, candidate ->
      val heightMetres = generated.base.heightAt(candidate.position.x, candidate.position.y)
      val position = Vec3L(
        x = (candidate.position.x / config.voxelSize).toLong(),
        y = (candidate.position.y / config.voxelSize).toLong(),
        z = config.voxelZOf(heightMetres).toLong()
      )

      MasterSpawnPoint(
        settlementIndex = candidate.settlementIndex,
        settlementName = candidate.name,
        tier = candidate.tier.label,
        population = candidate.population,
        position = position,
        rank = firstRank + offset,
      )
    }
  }

  companion object {
    /** Homes offered at once. */
    const val HOMES_OFFERED = 3

    /** Homes held in rank order, so a fallen town is replaced by the next largest. */
    const val HOMES_HELD = 12

    private val LOG = KotlinLogging.logger { }
  }
}
