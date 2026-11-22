package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.stronghold.persistence.StrongholdRecord
import net.bestia.zone.stronghold.persistence.StrongholdRecordRepository
import net.bestia.zone.stronghold.persistence.TreasureDropRow
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * Every guild's hold on a seed, in memory. Tick thread only after [restoreAll], and written through like
 * `TownRegistry`: the map changes at once, the row a moment later.
 */
@Service
class StrongholdRegistry(
  private val worldService: WorldService,
  private val repository: StrongholdRecordRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  private val byRank = HashMap<Int, Stronghold>()

  fun restoreAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    if (stale.isNotEmpty()) {
      repository.deleteAll(stale)
      LOG.warn { "Dropped ${stale.size} Strongholds of another world or pipeline" }
    }

    byRank.clear()
    current.associateTo(byRank) { it.rank to strongholdOf(it) }
    LOG.info { "Restored ${byRank.size} Strongholds" }
  }

  fun all(): Collection<Stronghold> {
    return byRank.values
  }

  fun on(rank: Int): Stronghold? {
    return byRank[rank]
  }

  fun heldBy(guildId: Long): List<Stronghold> {
    return byRank.values.filter { it.guildId == guildId }
  }

  fun put(stronghold: Stronghold) {
    byRank[stronghold.rank] = stronghold

    val row = StrongholdRecord(
      rank = stronghold.rank,
      guildId = stronghold.guildId,
      state = stronghold.state,
      siteStructureId = stronghold.siteStructureId,
      siteWorkedAt = stronghold.siteWorkedAt,
      plantedAt = stronghold.plantedAt,
      empyriteHp = stronghold.empyriteHp,
      defenseLevel = stronghold.progressOf(StrongholdTrack.DEFENSE).level,
      defenseSteps = stronghold.progressOf(StrongholdTrack.DEFENSE).steps,
      storageLevel = stronghold.progressOf(StrongholdTrack.STORAGE).level,
      storageSteps = stronghold.progressOf(StrongholdTrack.STORAGE).steps,
      economyLevel = stronghold.progressOf(StrongholdTrack.ECONOMY).level,
      economySteps = stronghold.progressOf(StrongholdTrack.ECONOMY).steps,
      hasSteward = stronghold.hasSteward,
      meleeWatchers = stronghold.watchersOf(WatcherKind.MELEE),
      archerWatchers = stronghold.watchersOf(WatcherKind.ARCHER),
      watcherStrength = stronghold.watcherStrength,
      dropsMade = stronghold.dropsMade,
      drops = stronghold.drops.mapTo(ArrayList()) { drop ->
        TreasureDropRow(drop.madeAt, drop.coins, drop.goods.entries.joinToString(",") { (id, amount) -> "$id:$amount" })
      },
      worldShapeVersion = worldService.record.shapeVersion,
      pipelineVersion = worldService.record.pipelineVersion
    )
    asyncJobExecutor.submit(JOB_KEY) {
      repository.save(row)
    }
  }

  fun remove(rank: Int) {
    if (byRank.remove(rank) == null) return

    asyncJobExecutor.submit(JOB_KEY) {
      repository.deleteById(rank)
    }
  }

  private fun strongholdOf(row: StrongholdRecord): Stronghold {
    val tracks = mapOf(
      StrongholdTrack.DEFENSE to TrackProgress(row.defenseLevel, row.defenseSteps),
      StrongholdTrack.STORAGE to TrackProgress(row.storageLevel, row.storageSteps),
      StrongholdTrack.ECONOMY to TrackProgress(row.economyLevel, row.economySteps),
    )

    return Stronghold(
      row.rank, row.guildId, row.state, row.siteStructureId, row.siteWorkedAt, row.plantedAt, row.empyriteHp, tracks,
      row.hasSteward,
      mapOf(WatcherKind.MELEE to row.meleeWatchers, WatcherKind.ARCHER to row.archerWatchers),
      row.watcherStrength,
      row.dropsMade,
      row.drops.map { drop -> TreasureDrop(drop.madeAt, drop.coins, goodsOf(drop.goods)) },
    )
  }

  private fun goodsOf(encoded: String): Map<Long, Int> {
    return encoded.split(",").filter { it.isNotBlank() }.associate { pair ->
      val (itemId, amount) = pair.split(":")
      itemId.toLong() to amount.toInt()
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** One key for every write, so a removal can never overtake the write before it. */
    const val JOB_KEY = "strongholds"
  }
}
