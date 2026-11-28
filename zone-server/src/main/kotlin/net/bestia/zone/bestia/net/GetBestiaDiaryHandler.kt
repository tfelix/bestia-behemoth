package net.bestia.zone.bestia.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.bestia.diary.BestiaDiary
import net.bestia.zone.bestia.diary.BestiaDiarySMSG
import net.bestia.zone.bestia.diary.BestiaHappened
import net.bestia.zone.bestia.diary.BestiaNewsSMSG
import net.bestia.zone.bestia.persistence.DiaryLine
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Sends the selected master's bestia diary, which the chat's Bestias tab shows before the live news. */
@Component
class GetBestiaDiaryHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val diary: BestiaDiary,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<GetBestiaDiaryCMSG> {

  override val wire = decoder(MessageCase.GET_BESTIA_DIARY) { accountId, _ -> GetBestiaDiaryCMSG(accountId) }

  override fun handle(msg: GetBestiaDiaryCMSG): Boolean {
    val masterId = connectionInfoService.activeMasterIdOf(msg.playerId) ?: return false

    // Spans of time are for the away report to add up; the tab tells what happened.
    val lines = diary.of(masterId)
      .filter { it.kind != BestiaHappened.Kind.STATE }
      .take(MAX_LINES)
      .map(::newsOf)
    outMessageProcessor.sendToPlayer(msg.playerId, BestiaDiarySMSG(lines))
    return true
  }

  private fun newsOf(line: DiaryLine): BestiaNewsSMSG {
    return BestiaNewsSMSG(
      playerBestiaId = line.playerBestiaId,
      kind = line.kind,
      atEpochMs = line.at.toEpochMilli(),
      items = line.itemAmounts(),
      place = line.place,
      reason = line.reason,
    )
  }

  private companion object {
    /** What the tab keeps at most, so a week of diary fits one message. */
    const val MAX_LINES = 300
  }
}
