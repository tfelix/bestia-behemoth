package net.bestia.zone.economy

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

/**
 * One settlement's books, for a settlement that has any.
 *
 * ### A row only where something happened
 *
 * The table is not one row per settlement. A world holds hundreds of towns and a player has touched a
 * handful, and every untouched one sits exactly at its derived reference - so there is nothing to store,
 * its step is a no-op, and the row is created on the first non-zero deviation and deleted again when
 * everything has decayed back inside a tolerance. That is the whole reason the economy can be said to
 * simulate the world rather than the part of it somebody is standing in.
 *
 * ### The deviations are a blob, not columns
 *
 * A column per commodity would need a migration every time the catalogue grows, and the catalogue is a
 * yml file that is expected to grow. What is stored is `id=value` pairs; a commodity that has since been
 * removed is dropped on read, and one that has since been added starts at zero, which is exactly right
 * because zero is where a settlement that never traded it would be.
 *
 * ### Two version stamps, for `WorldObjectDivergence`'s reasons
 *
 * [worldShapeVersion] catches a reseeded or resized world and [pipelineVersion] a rebuilt one - both,
 * because `pipelineVersion` folds stage and params versions but not the seed. Settlement indices are
 * dense and re-used, so a surviving row would not be orphaned, it would be applied to *a different town*.
 */
@Entity
@Table(name = "settlement_ledger")
class SettlementLedger(
  /** The dense settlement index, which is the join key everything downstream of the generator uses. */
  @Id
  @Column(name = "settlement", nullable = false)
  var settlement: Int = 0,

  /** `commodity=delta` pairs, newline separated. See the class note on why this is not columns. */
  @Lob
  @Column(name = "stock_deviation", nullable = false)
  var stockDeviation: String = "",

  @Lob
  @Column(name = "price_deviation", nullable = false)
  var priceDeviation: String = "",

  @Column(nullable = false)
  var treasury: Double = 0.0,

  /**
   * The game day the books were brought up to.
   *
   * Whole days, because [EconomyStep] carries the fraction rather than integrating it - which is what
   * makes catching a town up in one jump give the same answer as ticking it every day.
   */
  @Column(name = "last_step_day", nullable = false)
  var lastStepDay: Double = 0.0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {
  /** Wall clock, for a human reading the table. Never read by code. */
  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant = Instant.now()

  fun toState(): LedgerState {
    return LedgerState(
      deltaStock = decode(stockDeviation),
      deltaLogPrice = decode(priceDeviation),
      treasury = treasury,
      lastStepDay = lastStepDay,
    )
  }

  companion object {

    fun of(settlement: Int, state: LedgerState, shapeVersion: Long, pipelineVersion: Long): SettlementLedger {
      return SettlementLedger(
        settlement = settlement,
        stockDeviation = encode(state.deltaStock),
        priceDeviation = encode(state.deltaLogPrice),
        treasury = state.treasury,
        lastStepDay = state.lastStepDay,
        worldShapeVersion = shapeVersion,
        pipelineVersion = pipelineVersion,
      )
    }

    private fun encode(values: Map<String, Double>): String {
      return values.entries.joinToString("\n") { (id, value) -> "$id=$value" }
    }

    private fun decode(text: String): Map<String, Double> {
      return text.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
          val name = line.substringBefore('=')
          line.substringAfter('=').toDoubleOrNull()?.let { name to it }
        }
        .toMap()
    }
  }
}
