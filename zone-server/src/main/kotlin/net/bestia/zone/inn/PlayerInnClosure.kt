package net.bestia.zone.inn

import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.LetterService
import net.bestia.zone.prop.PlayerStructureDestroyedEvent
import net.bestia.zone.settlement.TownFellEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * A player inn that is knocked down, or whose town falls, is no save point any more. Its guests get their save point
 * cleared, so the next respawn picks the nearest start town, and a letter to say so.
 */
@Component
class PlayerInnClosure(
  private val masters: MasterRepository,
  private val letters: LetterService,
  private val jobs: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
) {

  @EventListener
  fun onDestroyed(event: PlayerStructureDestroyedEvent) {
    if (event.kind != StaticEntityKind.INN) return
    val door = event.position

    jobs.submit(event.structureId) {
      clear("The inn you slept at was torn down.") { masters.findAllSavedAt(door.x, door.y, door.z) }
    }
  }

  @EventListener
  fun onTownFell(event: TownFellEvent) {
    jobs.submit(event.townId) {
      clear("The sign of ${event.name} has fallen, and its inn closed with it.") { masters.findAllByHomeSettlementName(event.name) }
    }
  }

  private fun clear(why: String, guests: () -> List<Master>) {
    val cleared = transactions.execute {
      guests().onEach {
        it.spawnPosition = Vec3L.ZERO
        it.homeSettlementName = ""
      }.map { it.id }
    }.orEmpty()

    for (masterId in cleared) {
      letters.postNotice(masterId, SENDER_NAME, "Your save point is gone", "$why You will get up in the nearest start town.")
    }
  }

  private companion object {
    const val SENDER_NAME = "The Innkeepers' Guild"
  }
}
