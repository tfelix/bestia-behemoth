package net.bestia.zone.settlement

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.place.ecs.AreaNameRegistry
import net.bestia.zone.settlement.persistence.PlayerSettlement
import net.bestia.zone.settlement.persistence.PlayerSettlementRepository
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.math.ceil
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
  private val areaNames: AreaNameRegistry,
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
    put(town)
    asyncJobExecutor.submit(town.id) { repository.save(row) }

    return town
  }

  /** Replaces the town with [town] and stores what can change. The size is never stored; it is recounted. */
  fun update(town: Town) {
    put(town)

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
    put(town.copy(size = size))
  }

  fun remove(townId: Long) {
    if (byId.remove(townId) == null) return
    areaNames.remove(townId)

    asyncJobExecutor.submit(townId) { repository.deleteById(townId) }
  }

  /** Boot-time only, before the tick loop starts. */
  fun loadAll() {
    repository.findAll().forEach { put(townOf(it)) }

    LOG.info { "Loaded ${byId.size} player settlement(s)" }
  }

  /** A standing town is a place too, so whoever walks into it learns its name and where it ends. */
  private fun put(town: Town) {
    val previous = byId.put(town.id, town)
    if (previous != null && isSamePlace(previous, town)) return

    if (town.isStanding) {
      val radius = ceil(town.size.radiusMetres / worldService.config.voxelSize).toLong()
      areaNames.add(AreaNameRegistry.Entry(town.id, town.name, town.sign.x, town.sign.y, radius))
    } else {
      areaNames.remove(town.id)
    }
  }

  /** Sign hp changes every few seconds in a fight; only what the place index holds is worth re-announcing. */
  private fun isSamePlace(a: Town, b: Town): Boolean {
    return a.isStanding == b.isStanding && a.name == b.name && a.sign == b.sign &&
        a.size.radiusMetres == b.size.radiusMetres
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
