package net.bestia.zone.postal.parcel

import kotlin.math.ceil
import kotlin.math.max

/** What a parcel may weigh and what it costs to send, as the postal page of the design docs gives them. */
object Freight {

  /** Item weight units per kilogram, as `items.yml` counts weight. */
  const val UNITS_PER_KG = 100

  /** 10 kg. Bulk freight is work for Transport commissions, not for the post. */
  const val MAX_WEIGHT_UNITS = 10 * UNITS_PER_KG

  private const val GOLD_PER_KM_AND_KG = 10.0

  /** A courier is paid 60% of the fee; the rest stays with the reserve, which took all of it at sending. */
  private const val COURIER_SHARE = 0.6

  /** `10 * road_km * weight_kg`, rounded up like every fee, and at least 1. */
  fun fee(roadKm: Double, weightUnits: Int): Long {
    val kg = weightUnits.toDouble() / UNITS_PER_KG
    return max(1L, ceil(GOLD_PER_KM_AND_KG * roadKm * kg).toLong())
  }

  /** Rounded down, so the reserve never pays out more than its share allows. */
  fun courierPay(fee: Long): Long {
    return (fee * COURIER_SHARE).toLong()
  }
}
