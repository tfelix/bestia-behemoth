package net.bestia.zone.world

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

  @Transactional
  fun ensureComputed(): List<MasterSpawnPoint> {
    val existing = repository.findAll()
    if (existing.isNotEmpty()) return existing

    val generated = worldService.generated
    val config = generated.config

    val rows = SettlementSpawnPoints.choose(generated).map { candidate ->
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
        position = position
      )
    }

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

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
