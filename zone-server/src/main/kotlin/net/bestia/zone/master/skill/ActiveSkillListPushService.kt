package net.bestia.zone.master.skill

import net.bestia.zone.casting.essence.ActiveSkillListPush
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Builds the list as a refresh request would, off the tick: a Bestia's comes from the database. */
@Component
class ActiveSkillListPushService(
  private val connections: ConnectionInfoService,
  private val skillLists: SkillListService,
  private val out: OutMessageProcessor,
  private val asyncJobs: AsyncJobExecutor,
) : ActiveSkillListPush {

  override fun pushIfActive(accountId: AccountId, entityId: EntityId) {
    if (!connections.hasActiveSession(accountId) || connections.getActiveEntityId(accountId) != entityId) return

    asyncJobs.submit(key = connections.getMasterId(accountId)) {
      out.sendToPlayer(accountId, SkillListSMSG(entityId, skillLists.entriesFor(accountId, entityId)))
    }
  }
}
