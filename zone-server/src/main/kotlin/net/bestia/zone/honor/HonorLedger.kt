package net.bestia.zone.honor

import java.time.Duration
import java.time.Instant

/**
 * One honor value, kept as what was earned and what is owed, so that a loss heals without a tick.
 *
 * The value at any moment is `start + earned - debt`. Only the debt heals, and only once [HonorLayer.grace] has
 * passed since the last loss, so a Master who keeps offending never recovers and offline time counts in full.
 */
data class HonorLedger(
  val earned: Int = 0,
  val debt: Int = 0,
  val lastLossAt: Instant? = null,
  val permanent: Boolean = false,
) {

  fun valueAt(now: Instant, layer: HonorLayer): Int {
    return layer.start + earned - debtAt(now, layer)
  }

  /** Applies a signed amount: a loss adds to the debt, a gain to what was earned, up to the cap. */
  fun apply(amount: Int, now: Instant, layer: HonorLayer): HonorLedger {
    return when {
      amount < 0 -> withLoss(-amount, now, layer)
      amount > 0 -> withGain(amount, now, layer)
      else -> this
    }
  }

  private fun withLoss(loss: Int, now: Instant, layer: HonorLayer): HonorLedger {
    val owed = debtAt(now, layer) + loss
    val value = layer.start + earned - owed
    val becomesPermanent = permanent || (layer.permanentBelow != null && value < layer.permanentBelow)

    return copy(debt = owed, lastLossAt = now, permanent = becomesPermanent)
  }

  /** Gains past the cap are lost rather than banked, so honor cannot be saved up for a massacre. */
  private fun withGain(gain: Int, now: Instant, layer: HonorLayer): HonorLedger {
    val headroom = layer.cap - valueAt(now, layer)
    val kept = gain.coerceAtMost(headroom).coerceAtLeast(0)

    return copy(earned = earned + kept)
  }

  private fun debtAt(now: Instant, layer: HonorLayer): Int {
    val since = lastLossAt ?: return debt
    if (permanent) {
      return debt
    }

    val healing = Duration.between(since.plus(layer.grace), now)
    if (healing.isNegative) {
      return debt
    }

    val healed = healing.toDays() * layer.recoveryPerDay

    return (debt - healed).coerceAtLeast(0).toInt()
  }
}
