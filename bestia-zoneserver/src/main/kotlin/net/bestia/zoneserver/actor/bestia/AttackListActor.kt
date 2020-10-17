package net.bestia.zoneserver.actor.bestia

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.model.battle.PlayerAttackRepository
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import org.springframework.beans.factory.annotation.Qualifier

private val LOG = KotlinLogging.logger { }

data class AttackListRequest(
    val accountId: Long,
    val playerBestiaId: Long
)

data class AttackListResponse(
    override val accountId: Long,
    val playerBestiaId: Long,
    val attacks: List<AttackInfo>
) : AccountMessage {
  data class AttackInfo(
      val attackId: Long,
      val databaseName: String,
      val minLevel: Int
  )
}

/**
 * Allows the client to request a list of available attacks for the
 * bestia of choice.
 */
@Actor
class AttackListActor(
    private val playerBestiaRepository: PlayerBestiaRepository,
    private val playerAttackRepository: PlayerAttackRepository,
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val clientForwarder: ActorRef
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.matchRedirect(AttackListRequest::class.java, this::requestAttackList)
  }

  private fun requestAttackList(msg: AttackListRequest) {
    if (accountOwnsPlayerBestia(msg.accountId, msg.playerBestiaId)) {
      LOG.warn { "Account ${msg.accountId} requests not owned player bestia: ${msg.playerBestiaId}" }

      return
    }

    // Check that the bestia belongs to the player
    val knownAttacks = playerAttackRepository.getAllAttacksForBestia(msg.playerBestiaId)


    val response = AttackListResponse(
        accountId = msg.accountId,
        playerBestiaId = msg.playerBestiaId,
        attacks = knownAttacks.map { atk ->
          AttackListResponse.AttackInfo(
              attackId = atk.id,
              databaseName = atk.attack.databaseName,
              minLevel = atk.minLevel
          )
        }
    )

    clientForwarder.tell(response, self)
  }

  private fun accountOwnsPlayerBestia(accountId: Long, playerBestiaId: Long): Boolean {
    return playerBestiaRepository.findByOwnerIdAndId(accountId, playerBestiaId) != null
  }

  companion object {
    const val NAME = "attackList"
  }
}
