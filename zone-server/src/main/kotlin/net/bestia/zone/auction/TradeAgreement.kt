package net.bestia.zone.auction

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The Trade Agreement skill (`skills.yml` id 59): offers the targeted auction house owner a link. Once they accept,
 * every house of each shows the listings of the other's. The caster may have as many partners as the skill's level.
 */
@Component
class TradeAgreement(
  private val houses: PlayerHouses,
  private val links: TradeLinks,
  private val offers: TradeOffers,
  private val out: OutMessageProcessor,
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val masterId = world.get(casterId, Master::class)?.masterId ?: return OpError.REQUEST_REFUSED

    return when {
      houses.ownedBy(masterId).isEmpty() -> OpError.TRADE_AGREEMENT_NEEDS_HOUSE
      links.partnersOf(masterId).size >= skillLevel -> OpError.TRADE_AGREEMENT_LIMIT
      else -> null
    }
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.targetEntityId != null && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val casterMasterId = ctx.world.masterIdOf(ctx.casterId) ?: return null
    val casterAccountId = ctx.world.accountIdOf(ctx.casterId) ?: return null
    val targetId = ctx.targetEntityId ?: return null
    val targetMasterId = ctx.world.masterIdOf(targetId)
    val targetAccountId = ctx.world.accountIdOf(targetId)

    val isValidTarget = targetMasterId != null && targetAccountId != null && targetMasterId != casterMasterId &&
      houses.ownedBy(targetMasterId).isNotEmpty() && !links.isLinked(casterMasterId, targetMasterId)
    if (!isValidTarget) {
      out.sendToPlayer(casterAccountId, OperationErrorSMSG(OpError.TRADE_AGREEMENT_TARGET_INVALID))
      return null
    }

    val casterName = houses.ownedBy(casterMasterId).first().ownerName
    val offerId = offers.offer(TradeLinks.Partner(casterMasterId, casterName), casterAccountId, targetMasterId!!, ctx.skillLevel)
    out.sendToPlayer(targetAccountId!!, TradeAgreementOfferSMSG(offerId, casterName))

    return null
  }
}
