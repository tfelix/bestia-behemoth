package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.OrderSheetProto.OrderSheet
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Courage as WireCourage
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Job
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Overrides
import net.bestia.bnet.proto.SetBestiaAiConfigCmsgProto.SetBestiaAiConfigCMSG.Rule
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.Courage
import net.bestia.zone.ai.profile.OrderOverrides
import net.bestia.zone.ai.profile.OrderRule

/** Bestia orders to and from the game wire. */
object OrdersWire {

  /** Null when a job, the courage step or a rule is unknown, or the orders break a rule of [AiConfig]. */
  fun ordersFrom(
    job: Job,
    courage: WireCourage,
    fallbackJob: Job,
    overrides: Overrides,
    rules: List<Rule>,
    areaPlaceId: Long?,
    chestPlaceId: Long?,
  ): AiConfig? {
    val mainJob = BestiaJob.fromNameOrNull(job.name) ?: return null
    val step = Courage.fromNameOrNull(courage.name) ?: return null
    val fallback = fallbackFrom(fallbackJob).getOrElse { return null }
    val lines = rulesFrom(rules) ?: return null

    return AiConfig(mainJob, step, fallback, overridesFrom(overrides), lines, areaPlaceId, chestPlaceId).takeIf { it.isValid }
  }

  fun ordersFrom(sheet: OrderSheet): AiConfig? = ordersFrom(
    sheet.job, sheet.courage, sheet.fallbackJob, sheet.overrides, sheet.rulesList,
    sheet.takeIf { it.hasAreaPlaceId() }?.areaPlaceId,
    sheet.takeIf { it.hasChestPlaceId() }?.chestPlaceId,
  )

  fun sheetOf(orders: AiConfig): OrderSheet {
    val sheet = OrderSheet.newBuilder()
      .setJob(Job.valueOf(orders.job.name))
      .setCourage(WireCourage.valueOf(orders.courage.name))
      .setFallbackJob(fallbackTo(orders.fallbackJob))
      .setOverrides(overridesTo(orders.overrides))
      .addAllRules(rulesTo(orders.rules))
    orders.areaPlaceId?.let(sheet::setAreaPlaceId)
    orders.chestPlaceId?.let(sheet::setChestPlaceId)

    return sheet.build()
  }

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

  /** Null when a line names a condition or action this server does not know. */
  fun rulesFrom(wire: List<Rule>): List<OrderRule>? {
    return wire.map { rule ->
      val threshold = rule.takeIf { it.hasThreshold() }?.threshold
      OrderRule.fromNamesOrNull(rule.condition.name, threshold, rule.action.name) ?: return null
    }
  }

  fun rulesTo(rules: List<OrderRule>): List<Rule> = rules.map { rule ->
    val wire = Rule.newBuilder()
      .setCondition(Rule.Condition.valueOf(rule.condition.name))
      .setAction(Rule.Action.valueOf(rule.action.name))
    rule.threshold?.let(wire::setThreshold)
    wire.build()
  }

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
