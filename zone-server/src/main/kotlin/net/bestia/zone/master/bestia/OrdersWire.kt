package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Job
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Overrides
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.OrderOverrides

/** Bestia orders to and from the game wire. */
object OrdersWire {

  /** No fallback for [Job.JOB_UNSPECIFIED]; failure for a job this server does not know. */
  fun fallbackFrom(job: Job): Result<BestiaJob?> {
    if (job == Job.JOB_UNSPECIFIED) return Result.success(null)

    return BestiaJob.fromNameOrNull(job.name)?.let { Result.success(it) }
      ?: Result.failure(IllegalArgumentException("Unknown fallback job $job"))
  }

  fun fallbackTo(job: BestiaJob?): Job = job?.let { Job.valueOf(it.name) } ?: Job.JOB_UNSPECIFIED

  fun overridesFrom(wire: Overrides): OrderOverrides = OrderOverrides(
    retreatBelowHealthPct = wire.takeIf { it.hasRetreatBelowHealthPct() }?.retreatBelowHealthPct,
    eatBelowStaminaPct = wire.takeIf { it.hasEatBelowStaminaPct() }?.eatBelowStaminaPct,
    healBelowHealthPct = wire.takeIf { it.hasHealBelowHealthPct() }?.healBelowHealthPct,
    haulAtLoadPct = wire.takeIf { it.hasHaulAtLoadPct() }?.haulAtLoadPct,
    areaRadius = wire.takeIf { it.hasAreaRadius() }?.areaRadius,
  )

  fun overridesTo(overrides: OrderOverrides): Overrides {
    val wire = Overrides.newBuilder()
    overrides.retreatBelowHealthPct?.let(wire::setRetreatBelowHealthPct)
    overrides.eatBelowStaminaPct?.let(wire::setEatBelowStaminaPct)
    overrides.healBelowHealthPct?.let(wire::setHealBelowHealthPct)
    overrides.haulAtLoadPct?.let(wire::setHaulAtLoadPct)
    overrides.areaRadius?.let(wire::setAreaRadius)

    return wire.build()
  }
}
