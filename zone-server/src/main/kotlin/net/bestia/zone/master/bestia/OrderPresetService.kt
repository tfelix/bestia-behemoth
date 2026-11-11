package net.bestia.zone.master.bestia

import net.bestia.zone.account.persistence.OrderPreset
import net.bestia.zone.account.persistence.OrderPresetRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ai.profile.AiConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A master's named orders, and keeping the bestias that follow one in step with it. Callers check ownership. */
@Service
class OrderPresetService(
  private val presets: OrderPresetRepository,
  private val playerBestias: PlayerBestiaRepository,
  private val aiConfigs: BestiaAiConfigService,
  private val places: NamedPlaces,
) {

  /** Why a preset could not be saved. */
  enum class Refusal { NOT_FOUND, INVALID_NAME, NAME_TAKEN, TOO_MANY, INVALID_ORDERS }

  class Saved(val preset: OrderPreset?, val refusal: Refusal?)

  /** Each preset with how many bestias follow it. */
  @Transactional(readOnly = true)
  fun list(masterId: Long): List<Pair<OrderPreset, Int>> {
    return presets.findAllByMasterIdOrderByName(masterId).map { it to playerBestias.findAllByOrderPresetId(it.id).size }
  }

  /** Creates a preset, or with [presetId] changes one, and gives every bestia that follows it the change. */
  @Transactional
  fun save(masterId: Long, presetId: Long?, name: String, orders: AiConfig): Saved {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.length > OrderPreset.NAME_LENGTH) return refused(Refusal.INVALID_NAME)
    if (!orders.isValid || !places.fitFor(masterId, orders)) return refused(Refusal.INVALID_ORDERS)

    val preset = if (presetId == null) {
      if (presets.countByMasterId(masterId) >= OrderPreset.MAX_PER_MASTER) return refused(Refusal.TOO_MANY)
      OrderPreset(masterId, trimmed)
    } else {
      presets.findByIdAndMasterId(presetId, masterId) ?: return refused(Refusal.NOT_FOUND)
    }
    if (preset.name != trimmed || presetId == null) {
      if (presets.existsByMasterIdAndName(masterId, trimmed)) return refused(Refusal.NAME_TAKEN)
    }

    preset.name = trimmed
    preset.orders = orders.copy(overrides = orders.overrides.clamped())
    val saved = presets.save(preset)
    aiConfigs.reapply(saved)

    return Saved(saved, null)
  }

  /** False when the master has no such preset. Its bestias keep the orders they had, as their own. */
  @Transactional
  fun delete(masterId: Long, presetId: Long): Boolean {
    val preset = presets.findByIdAndMasterId(presetId, masterId) ?: return false

    aiConfigs.unlinkAll(preset.id)
    presets.delete(preset)
    return true
  }

  private fun refused(refusal: Refusal) = Saved(null, refusal)
}
