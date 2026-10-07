package net.bestia.zone.battle.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor

/** Tells a player why the attack or harmful skill they aimed did nothing. */
object HarmRefusal {

  fun send(outMessageProcessor: OutMessageProcessor, playerId: Long, verdict: DamageGate.Verdict) {
    val code = when (verdict) {
      DamageGate.Verdict.ADMITTED -> return
      DamageGate.Verdict.WARDED -> OpError.COMBAT_TARGET_WARDED
      DamageGate.Verdict.IMMUNE -> {
        // An honest client offers talk, not a fight, on anything immune, so there is nothing to word for the player.
        LOG.warn { "Account $playerId aimed harm at an immune target" }
        OpError.REQUEST_REFUSED
      }
      // The client cannot tell a player's own station from anybody else's, so a skill may snap onto it.
      DamageGate.Verdict.OWN -> OpError.REQUEST_REFUSED
    }

    outMessageProcessor.sendToPlayer(playerId, OperationErrorSMSG(code))
  }

  private val LOG = KotlinLogging.logger { }
}
