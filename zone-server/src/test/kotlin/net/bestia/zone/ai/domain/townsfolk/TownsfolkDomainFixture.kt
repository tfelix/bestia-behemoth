package net.bestia.zone.ai.domain.townsfolk

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.perception.SettlementWork
import net.bestia.zone.ecs.spawn.townsfolk.IndoorRegistry
import net.bestia.zone.economy.Trade
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.TestNavigation
import kotlin.random.Random

/**
 * Collaborators the townsfolk action templates need in order to be built.
 *
 * Movement over flat ground because the real service is cheap, and a workshop that knows no trades, which
 * is what everybody outside the economy tests is: a planning test asserts which actions the planner
 * chains rather than ticking their trees, so these have to exist rather than work.
 */
object TownsfolkDomainFixture {

  /** Nobody has a recipe, so every shift is the plain kind. */
  val NO_TRADES = object : SettlementWork {
    override fun tradeOf(business: String?): Trade? = null
    override fun canSupply(settlement: Int, trade: Trade): Boolean = true
    override fun supplierNear(at: Vec3L, business: String?): SettlementWork.Supply? = null
  }

  /** Arbitrary and fixed; see `AiPipelineFixture.DEFAULT_SEED` for why arbitrary is the point. */
  private const val SEED = 20260914L

  fun resolver(
    actionIds: List<String> = TownsfolkDomain.actionIds.toList(),
    indoors: IndoorRegistry = IndoorRegistry(),
    work: SettlementWork = NO_TRADES,
  ): ActionResolver {
    return TownsfolkDomain.resolver(
      actionIds,
      TownsfolkDomain.Collaborators(
        Locomotion(TestNavigation.service(), Random(SEED)), indoors, work, TownsfolkProduction()
      )
    )
  }
}
