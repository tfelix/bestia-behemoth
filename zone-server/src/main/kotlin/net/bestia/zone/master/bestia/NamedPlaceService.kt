package net.bestia.zone.master.bestia

import net.bestia.zone.account.persistence.OrderPlace
import net.bestia.zone.account.persistence.OrderPlaceRepository
import net.bestia.zone.account.persistence.OrderPresetRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ai.domain.bestia.NamedPlace
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.OrderOverrides
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Naming, moving and forgetting a master's places. Callers check ownership. A moved place reaches its bestias by
 * itself, through their senses; a deleted one is taken out of every order that names it.
 */
@Service
class NamedPlaceService(
  private val rows: OrderPlaceRepository,
  private val places: NamedPlaces,
  private val playerBestias: PlayerBestiaRepository,
  private val presets: OrderPresetRepository,
  private val aiConfigs: BestiaAiConfigService,
) {

  enum class Refusal { NOT_FOUND, INVALID_NAME, NAME_TAKEN, TOO_MANY, NOT_A_CHEST }

  class Saved(val place: NamedPlace?, val refusal: Refusal?)

  /** A work area, or with [placeId] the area moved, resized or renamed. */
  @Transactional
  fun saveArea(masterId: Long, placeId: Long?, name: String, center: Vec3L, radius: Long): Saved {
    val clampedRadius = radius.coerceIn(OrderOverrides.LIMITS.areaRadius.first.toLong(), OrderOverrides.LIMITS.areaRadius.last.toLong())

    return save(masterId, placeId, name, NamedPlace.Kind.AREA) { existing ->
      (existing ?: OrderPlace(masterId, name, NamedPlace.Kind.AREA, center.x, center.y, clampedRadius)).also {
        it.centerX = center.x
        it.centerY = center.y
        it.radius = clampedRadius
      }
    }
  }

  /** A name for one of the master's chests, which stands at [at]. Checking that it is theirs is the caller's. */
  @Transactional
  fun saveChest(masterId: Long, placeId: Long?, name: String, chestStructureId: Long, at: Vec3L): Saved {
    return save(masterId, placeId, name, NamedPlace.Kind.CHEST) { existing ->
      existing ?: OrderPlace(masterId, name, NamedPlace.Kind.CHEST, at.x, at.y, 0, chestStructureId)
    }
  }

  /** False when the master has no such place. Orders that named it go back to home and the nearest chest. */
  @Transactional
  fun delete(masterId: Long, placeId: Long): Boolean {
    val row = rows.findByIdAndMasterId(placeId, masterId) ?: return false

    presets.findAllByMasterIdOrderByName(masterId).filter { names(it.orders, placeId) }.forEach { preset ->
      preset.orders = preset.orders.without(placeId)
      aiConfigs.reapply(presets.save(preset))
    }
    playerBestias.findAllByMasterId(masterId)
      .filter { it.orderPresetId == null && names(it.aiConfig, placeId) }
      .forEach { aiConfigs.apply(it.id, it.aiConfig.without(placeId)) }

    rows.delete(row)
    places.remove(placeId)
    return true
  }

  private fun save(
    masterId: Long,
    placeId: Long?,
    name: String,
    kind: NamedPlace.Kind,
    build: (OrderPlace?) -> OrderPlace,
  ): Saved {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.length > OrderPlace.NAME_LENGTH) return refused(Refusal.INVALID_NAME)

    val existing = placeId?.let { rows.findByIdAndMasterId(it, masterId)?.takeIf { row -> row.kind == kind } ?: return refused(Refusal.NOT_FOUND) }
    if (existing == null && rows.countByMasterId(masterId) >= OrderPlace.MAX_PER_MASTER) return refused(Refusal.TOO_MANY)
    if (existing?.name != trimmed && rows.existsByMasterIdAndName(masterId, trimmed)) return refused(Refusal.NAME_TAKEN)

    val row = build(existing).also { it.name = trimmed }
    val saved = rows.save(row)
    places.put(saved)
    return Saved(places.placeOf(saved.id), null)
  }

  private fun names(orders: AiConfig, placeId: Long) = orders.areaPlaceId == placeId || orders.chestPlaceId == placeId

  private fun AiConfig.without(placeId: Long) = copy(
    areaPlaceId = areaPlaceId?.takeIf { it != placeId },
    chestPlaceId = chestPlaceId?.takeIf { it != placeId },
  )

  private fun refused(refusal: Refusal) = Saved(null, refusal)
}
