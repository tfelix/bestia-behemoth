package net.bestia.zone.account.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.OrderOverrides

/**
 * Named orders a master gives to many bestias at once. Bestias stay linked: a change here reaches every one of them,
 * except the numbers a bestia set for itself. See `BestiaAiConfigService`.
 */
@Entity
@Table(
  name = "order_preset",
  indexes = [Index(columnList = "master_id, name", unique = true)],
)
class OrderPreset(
  @Column(name = "master_id", nullable = false)
  val masterId: Long,

  @Column(nullable = false, length = NAME_LENGTH)
  var name: String,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  var orders: AiConfig
    get() = storedOrders.copy(overrides = storedOverrides ?: OrderOverrides())
    set(value) {
      storedOrders = value
      storedOverrides = value.overrides
    }

  @Embedded
  private var storedOrders: AiConfig = AiConfig()

  /** Apart, and nullable, for the reason `PlayerBestia.aiConfig` gives. */
  @Embedded
  private var storedOverrides: OrderOverrides? = null

  companion object {
    const val NAME_LENGTH = 32

    /** Enough for a role per kind of work, with room to try things out. */
    const val MAX_PER_MASTER = 20
  }
}
