package net.bestia.zone.settlement

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.settlement.persistence.PlayerSettlement
import net.bestia.zone.settlement.persistence.PlayerSettlementRepository
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.math.hypot

/**
 * Every player settlement, in memory. Tick-thread only, and written through like
 * [net.bestia.zone.prop.PlayerStructureRegistry]: the map changes at once, the row a moment later.
 */
@Service
class TownRegistry(
  private val repository: PlayerSettlementRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldService: WorldService,
) {

  private val byId = HashMap<Long, Town>()

  fun all(): Collection<Town> {
    return byId.values
  }

  fun of(townId: Long): Town? {
    return byId[townId]
  }

  /** The town [masterId] founded and still holds, if any. */
  fun heldBy(masterId: Long): Town? {
    return byId.values.firstOrNull { it.isHeld && it.founderMasterId == masterId }
  }

  fun isNameTaken(name: String): Boolean {
    return byId.values.any { it.name.equals(name, ignoreCase = true) }
  }

  /** The standing town whose sphere holds [position]. Spheres never overlap: signs stand 5 km apart. */
  fun standingAt(position: Vec3L): Town? {
    return byId.values.firstOrNull { it.isStanding && metresBetween(it.sign, position) <= it.size.radiusMetres }
  }

  fun metresBetween(a: Vec3L, b: Vec3L): Double {
    return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) * worldService.config.voxelSize
  }

  /** Named by its sign site's structure id, so the row can be written later, off the tick. */
  fun create(
    siteStructureId: Long,
    name: String,
    founderMasterId: Long,
    founderAccountId: AccountId,
    founderName: String,
    sign: Vec3L,
    now: Instant
  ): Town {
    val row = PlayerSettlement(
      id = siteStructureId,
      name = name,
      founderMasterId = founderMasterId,
      founderAccountId = founderAccountId,
      founderName = founderName,
      signX = sign.x,
      signY = sign.y,
      signZ = sign.z,
      state = PlayerSettlement.State.SITE,
      siteStructureId = siteStructureId,
      siteWorkedAt = now,
      signHp = TownGrowth.BASE_SIGN_HP,
    )
    val town = townOf(row)
    byId[town.id] = town
    asyncJobExecutor.submit(town.id) { repository.save(row) }

    return town
  }

  /** Replaces the town with [town] and stores what can change. The size is never stored; it is recounted. */
  fun update(town: Town) {
    byId[town.id] = town

    asyncJobExecutor.submit(town.id) {
      repository.findById(town.id).ifPresent { row ->
        row.founderMasterId = town.founderMasterId
        row.founderAccountId = town.founderAccountId
        row.founderName = town.founderName
        row.signZ = town.sign.z
        row.state = town.state
        row.siteStructureId = town.siteStructureId
        row.siteWorkedAt = town.siteWorkedAt
        row.signHp = town.signHp
        row.fellAt = town.fellAt
        repository.save(row)
      }
    }
  }

  /** Memory only: the size is recounted from the buildings rather than stored. */
  fun resize(townId: Long, size: TownGrowth.Size) {
    val town = byId[townId] ?: return
    byId[townId] = town.copy(size = size)
  }

  fun remove(townId: Long) {
    if (byId.remove(townId) == null) return

    asyncJobExecutor.submit(townId) { repository.deleteById(townId) }
  }

  /** Boot-time only, before the tick loop starts. */
  fun loadAll() {
    repository.findAll().forEach { byId[it.id] = townOf(it) }

    LOG.info { "Loaded ${byId.size} player settlement(s)" }
  }

  private fun townOf(row: PlayerSettlement): Town {
    return Town(
      id = row.id,
      name = row.name,
      founderMasterId = row.founderMasterId,
      founderAccountId = row.founderAccountId,
      founderName = row.founderName,
      sign = Vec3L(row.signX, row.signY, row.signZ),
      state = row.state,
      siteStructureId = row.siteStructureId,
      siteWorkedAt = row.siteWorkedAt,
      signHp = row.signHp,
      fellAt = row.fellAt,
    )
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
