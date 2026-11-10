package net.bestia.zone.battle.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** A player right-clicked a buff icon to drop the buff from the entity they control. */
@Component
class CancelStatusEffectHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val statusEffects: StatusEffectService,
) : TickMessageHandler<CancelStatusEffectCMSG> {
  override val wire = decoder(MessageCase.CANCEL_STATUS_EFFECT) { accountId, envelope ->
    CancelStatusEffectCMSG.fromBnet(accountId, envelope.cancelStatusEffect)
  }

  override fun handle(world: World, msg: CancelStatusEffectCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (!statusEffects.cancelByBearer(world, activeEntityId, msg.effectId)) {
      LOG.debug { "Entity $activeEntityId may not drop status effect ${msg.effectId}, ignoring" }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
