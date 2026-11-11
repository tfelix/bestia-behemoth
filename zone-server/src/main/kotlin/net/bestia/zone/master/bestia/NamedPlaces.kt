package net.bestia.zone.master.bestia

import net.bestia.zone.account.persistence.OrderPlace
import net.bestia.zone.account.persistence.OrderPlaceRepository
import net.bestia.zone.ai.domain.bestia.NamedPlace
import net.bestia.zone.ai.domain.bestia.PlaceLookup
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

/**
 * Every master's named places, in memory, so a sense can read them on the tick. Loaded at boot; [NamedPlaceService]
 * keeps it in step with the table.
 */
@Component
class NamedPlaces(
  private val rows: OrderPlaceRepository,
) : PlaceLookup {

  private val byId = ConcurrentHashMap<Long, NamedPlace>()

  @Transactional(readOnly = true)
  fun loadAll() {
    rows.findAll().forEach(::put)
  }

  override fun placeOf(id: Long): NamedPlace? = byId[id]

  fun of(masterId: Long): List<NamedPlace> = byId.values.filter { it.masterId == masterId }.sortedBy { it.name }

  /** Whether the places [orders] name are [masterId]'s, and of the kind each is named for. */
  fun fitFor(masterId: Long, orders: AiConfig): Boolean {
    return fits(masterId, orders.areaPlaceId, NamedPlace.Kind.AREA) && fits(masterId, orders.chestPlaceId, NamedPlace.Kind.CHEST)
  }

  fun put(row: OrderPlace) {
    byId[row.id] = NamedPlace(
      row.id, row.masterId, row.name, row.kind, Vec3L(row.centerX, row.centerY, 0), row.radius, row.chestStructureId,
    )
  }

  fun remove(id: Long) {
    byId.remove(id)
  }

  private fun fits(masterId: Long, placeId: Long?, kind: NamedPlace.Kind): Boolean {
    if (placeId == null) return true
    val place = byId[placeId] ?: return false

    return place.masterId == masterId && place.kind == kind
  }
}
