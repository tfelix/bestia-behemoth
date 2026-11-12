package net.bestia.zone.master.skill

import net.bestia.zone.casting.plagiarism.SkillListPush
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

@Component
class MasterSkillListPush(
  private val masterSkillListBuilder: MasterSkillListBuilder,
  private val outMessageProcessor: OutMessageProcessor,
) : SkillListPush {

  override fun push(world: World, masterEntityId: EntityId) {
    val accountId = world.get(masterEntityId, Account::class)?.accountId ?: return
    val entries = masterSkillListBuilder.entriesFor(world, masterEntityId)

    outMessageProcessor.sendToPlayer(accountId, SkillListSMSG(masterEntityId, entries))
  }
}
