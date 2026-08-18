package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.item.CarryCapacity
import net.bestia.zone.account.GetSelfCMSG
import net.bestia.zone.message.SelfSMSG
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional


@Component
class GetSelfHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val connectionInfoService: ConnectionInfoService,
  private val bestiaInfoFactory: BestiaInfoFactory,
  private val masterRepository: MasterRepository,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<GetSelfCMSG> {
  override val handles = GetSelfCMSG::class

  /**
   * Note this runs `readOnly = true` and now also briefly waits for the world lock inside that
   * transaction (at most one tick). All the DB reads happen in [getSelfInfo] first, so nothing does
   * I/O while holding the lock; `world.send` is the async alternative if that ever becomes a
   * problem.
   */
  @Transactional(readOnly = true)
  override fun handle(msg: GetSelfCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val selfInfo = getSelfInfo(msg.playerId)

    outMessageProcessor.sendToPlayer(msg.playerId, selfInfo)

    // Strictly after the send: the components below reach the client on the *next* tick, and the
    // client's UI drops any entity message that arrives before it has learned its master entity id
    // from this SelfSMSG.
    resyncOwnerComponents(selfInfo.masterEntityId)

    return true
  }

  /**
   * Re-pushes every owner-only component the master HUD and status window are built from. They are
   * all [net.bestia.zone.ecs.core.Dirtyable] and pushed on change, but a master spawns with pools
   * already full and attributes already settled, so that first push at spawn is the *only* one -
   * and it races the client still loading its game scene. A lost push is otherwise never made good
   * on, leaving the UI showing its scene placeholders indefinitely.
   *
   * Same sanctioned resync as [net.bestia.zone.item.inventory.GetInventoryHandler]: nothing
   * changed, so `markDirty()` is what requests the resend.
   */
  private fun resyncOwnerComponents(masterEntityId: EntityId) {
    world.modify(masterEntityId) { id ->
      get(id, Health::class)?.markDirty()
      get(id, Mana::class)?.markDirty()
      get(id, Stamina::class)?.markDirty()
      get(id, CarryCapacity::class)?.markDirty()
      get(id, Exp::class)?.markDirty()
      get(id, Level::class)?.markDirty()
      get(id, StatusValues::class)?.markDirty()
      get(id, BaseStatusValues::class)?.markDirty()
      get(id, StatusPoints::class)?.markDirty()
      get(id, SkillPoints::class)?.markDirty()
    } ?: LOG.warn { "Cannot resync components, master entity $masterEntityId is not alive" }
  }

  private fun getSelfInfo(accountId: AccountId): SelfSMSG {
    val masterId = connectionInfoService.getMasterId(accountId)
    val selectedMasterEntityId = connectionInfoService.getSelectedMasterEntityId(accountId)
    val bestiaEntities = connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)

    // Fetch all player bestias for this master in one query
    val bestiaInfos = bestiaInfoFactory.getBestiaInfo(bestiaEntities)

    return SelfSMSG(
      masterId = masterId,
      masterEntityId = selectedMasterEntityId,
      availableBestias = bestiaInfos,
      hasPerformedMasterRitual = masterRepository.findByIdOrThrow(masterId).hasPerformedMasterRitual
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
