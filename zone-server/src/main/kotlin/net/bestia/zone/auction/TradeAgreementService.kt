package net.bestia.zone.auction

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/** Answers a Trade Agreement offer. On the tick, which knows the answering master's skills. */
@Service
class TradeAgreementService(
  private val offers: TradeOffers,
  private val links: TradeLinks,
  private val houses: PlayerHouses,
  private val skills: SkillRepository,
  private val out: OutMessageProcessor,
) {

  /** Resolved at boot, after the skills are imported: the tick may not query the database. */
  private var agreementSkillId: Long? = null

  fun resolveSkills() {
    agreementSkillId = skills.findByIdentifier(SkillId.M_TRADE_AGREEMENT)?.id
  }

  fun answer(world: World, actorId: EntityId, offerId: Long, accept: Boolean) {
    val master = world.get(actorId, Master::class) ?: return
    val accountId = world.get(actorId, Account::class)?.accountId ?: return
    val offer = offers.take(offerId, master.masterId) ?: return

    if (!accept) {
      out.sendToPlayer(offer.fromAccountId, TradeLinkNoticeSMSG(TradeLinkNoticeSMSG.Notice.DECLINED, master.name))
      return
    }

    val level = agreementSkillId?.let { world.get(actorId, KnownSkills::class)?.levelOf(it) } ?: 0
    val mayLink = level > 0 && houses.ownedBy(master.masterId).isNotEmpty() &&
      links.partnersOf(master.masterId).size < level &&
      links.partnersOf(offer.from.masterId).size < offer.fromLevel &&
      !links.isLinked(master.masterId, offer.from.masterId)
    if (!mayLink) {
      out.sendToPlayer(accountId, OperationErrorSMSG(OpError.TRADE_AGREEMENT_TARGET_INVALID))
      return
    }

    links.link(offer.from, TradeLinks.Partner(master.masterId, master.name))
    out.sendToPlayer(accountId, TradeLinkNoticeSMSG(TradeLinkNoticeSMSG.Notice.LINKED, offer.from.name))
    out.sendToPlayer(offer.fromAccountId, TradeLinkNoticeSMSG(TradeLinkNoticeSMSG.Notice.LINKED, master.name))
  }
}
