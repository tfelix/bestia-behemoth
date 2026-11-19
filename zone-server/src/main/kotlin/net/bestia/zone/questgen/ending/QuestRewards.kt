package net.bestia.zone.questgen.ending

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.update
import net.bestia.zone.honor.HonorService
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemIntent
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemsIntent
import net.bestia.zone.questgen.QuestgenConfig
import net.bestia.zone.questgen.plan.QuestPlan
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * What finishing a quest of the world is worth. Paid on the tick to the master who finished it, who is being
 * played: the last step was their own deed a moment ago.
 */
@Component
class QuestRewards(
  private val world: WorldView,
  private val sessions: ConnectionInfoService,
  private val honor: HonorService,
  private val economy: SettlementEconomyService,
  private val commodityItems: CommodityItems,
  private val config: QuestgenConfig,
  private val masters: MasterEntityResolver = MasterEntityResolver(sessions),
) {

  enum class Ending {
    /** The master keeps what the quest was about. The thing itself is the reward. */
    KEEP,

    /** The master brought it to the town that wants it. */
    RETURN;

    companion object {

      /** A quest that ended with its bring step was a return. Any other last step leaves the thing with the master. */
      fun of(plan: QuestPlan, lastStage: Int): Ending {
        return if (plan.bringStep?.value?.objective?.stage == lastStage) RETURN else KEEP
      }
    }
  }

  fun pay(masterId: Long, plan: QuestPlan, ending: Ending) {
    val accountId = sessions.activeMasters().entries.firstOrNull { it.value == masterId }?.key
    val masterEntityId = accountId?.let(masters::selectedMasterEntityOf)
    if (masterEntityId == null) {
      LOG.warn { "Master $masterId finished the quest of ${plan.seed.subject} but is not played, so nothing is paid" }
      return
    }

    world.post { payOnTick(this, masterEntityId, masterId, plan, ending) }
  }

  private fun payOnTick(world: World, masterEntityId: EntityId, masterId: Long, plan: QuestPlan, ending: Ending) {
    val name = world.get(masterEntityId, Master::class)?.name ?: return
    world.update(masterEntityId, { GainExp() }) { it.value += plan.suggestedLevel * config.expPerLevel }

    if (plan.seed.kind == QuestSeed.Kind.NEWS) {
      honor.changeSettlement(masterId, name, plan.seed.subject.index, config.newsHonor)
    }

    val home = plan.broughtTo ?: return
    when (ending) {
      Ending.KEEP -> honor.changeSettlement(masterId, name, home, -config.keepHonorLoss)
      Ending.RETURN -> {
        honor.changeSettlement(masterId, name, home, config.returnHonor)
        payCoins(world, masterEntityId, economy.payReward(home, config.returnTreasuryShare, config.returnCoinsCap))
      }
    }
  }

  /** Added to whatever the master is already due this tick, because a second intent would replace the first. */
  private fun payCoins(world: World, masterEntityId: EntityId, coins: Long) {
    val coinId = commodityItems.coinItemId() ?: return
    if (coins <= 0) {
      return
    }

    val due = world.get(masterEntityId, CreateItemsIntent::class)?.grants.orEmpty()
    world.add(masterEntityId, CreateItemsIntent(due + CreateItemIntent(coinId, coins.toInt())))
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
