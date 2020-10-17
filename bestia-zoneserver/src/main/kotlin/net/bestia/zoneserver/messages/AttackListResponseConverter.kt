package net.bestia.zoneserver.messages

import net.bestia.messages.proto.AttackProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.actor.bestia.AttackListRequest
import net.bestia.zoneserver.actor.bestia.AttackListResponse
import org.springframework.stereotype.Component

@Component
class AttackListConverter :
    MessageConverterOut<AttackListResponse>,
    MessageConverterIn<AttackListRequest> {
  private val builder = AttackProtos.AttackListResponse.newBuilder()
  private val attackBuilder = AttackProtos.LearnedAttack.newBuilder()

  override fun convertToPayload(msg: AttackListResponse): ByteArray {
    builder.clear()

    builder.playerBestiaId = msg.playerBestiaId

    val attacks = msg.attacks.map { atk ->
      attackBuilder.setAttackDbName(atk.databaseName)
          .setMinLevel(atk.minLevel)
          .setAttackId(atk.attackId)
          .build()
    }

    return wrap {
      it.attackListResponse = builder.addAllAttacks(attacks)
          .setPlayerBestiaId(msg.playerBestiaId)
          .build()
    }
  }

  override fun convertToMessage(accountId: Long, msg: MessageProtos.Wrapper): AttackListRequest {
    return AttackListRequest(
        accountId = accountId,
        playerBestiaId = msg.attackListRequest.playerBestiaId
    )
  }

  override val fromMessage = AttackListResponse::class.java
  override val fromPayload = MessageProtos.Wrapper.PayloadCase.ATTACK_LIST_REQUEST
}