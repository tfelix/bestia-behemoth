package net.bestia.zone.bestia.diary

import net.bestia.zone.battle.ecs.damage.OwnedBestiaDiedEvent
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock

/** Tells a player what their bestias do while they play. A state line is for the away report only. */
@Component
class BestiaNewsNotifier(
  private val connections: ConnectionInfoService,
  private val out: OutMessageProcessor,
  private val clock: Clock = Clock.systemUTC(),
) {

  @EventListener
  fun onHappened(happened: BestiaHappened) {
    if (happened.kind == BestiaHappened.Kind.STATE) return
    val accountId = playingAccountOf(happened.masterId) ?: return

    out.sendToPlayer(
      accountId,
      BestiaNewsSMSG(
        happened.playerBestiaId, happened.kind, clock.millis(), happened.items, happened.place, happened.reason,
      ),
    )
  }

  /** A death reaches the diary without a [BestiaHappened] of its own. */
  @EventListener
  fun onDied(died: OwnedBestiaDiedEvent) {
    if (!connections.isPlaying(died.accountId, died.masterId)) return
    val kind = if (died.lostForGood) BestiaHappened.Kind.LOST else BestiaHappened.Kind.DIED

    out.sendToPlayer(died.accountId, BestiaNewsSMSG(died.playerBestiaId, kind, clock.millis()))
  }

  private fun playingAccountOf(masterId: Long): Long? {
    return connections.activeMasters().entries.firstOrNull { (_, playing) -> playing == masterId }?.key
  }
}
