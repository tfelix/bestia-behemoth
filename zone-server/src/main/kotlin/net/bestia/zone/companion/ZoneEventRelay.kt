package net.bestia.zone.companion

import net.bestia.bus.EventPublisher
import net.bestia.bus.contract.ZoneEvent
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.battle.ecs.damage.OwnedBestiaDiedEvent
import net.bestia.zone.cartography.chart.ChartsChangedEvent
import net.bestia.zone.compendium.CompendiumChangedEvent
import net.bestia.zone.master.HomeFellEvent
import net.bestia.zone.master.bestia.BestiaAiConfigChangedEvent
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.StructureCompletedEvent
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.context.event.EventListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Turns what happens in the zone into [ZoneEvent]s on the bus. Some of these are raised on the tick, so a listener
 * here only ever reads memory and hands over; anything that needs the database goes to a DB job first.
 */
@Component
class ZoneEventRelay(
  private val events: EventPublisher,
  private val connections: ConnectionInfoService,
  private val masters: MasterRepository,
  private val playerBestias: PlayerBestiaRepository,
  private val jobs: AsyncJobExecutor,
) {

  /** Only while nobody plays the master: a player at the keyboard has just watched it happen. */
  @EventListener
  fun onBestiaDied(event: OwnedBestiaDiedEvent) {
    if (connections.hasActiveSession(event.accountId)) {
      return
    }

    jobs.submit(event.masterId) {
      val bestia = playerBestias.findByIdOrNull(event.playerBestiaId) ?: return@submit
      events.publish(
        ZoneEvent.BestiaDiedAway(event.accountId, event.masterId, event.playerBestiaId, bestia.name, bestia.bestia.identifier)
      )
    }
  }

  @EventListener
  fun onStructureCompleted(event: StructureCompletedEvent) {
    events.publish(ZoneEvent.ConstructionDone(event.ownerAccountId, event.ownerMasterId, event.kind))
  }

  @EventListener
  fun onHomeFell(event: HomeFellEvent) {
    for (master in event.masters) {
      events.publish(ZoneEvent.HomeFell(master.accountId, master.masterId, master.name, event.fallen, event.newHome))
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  fun onStanceChanged(event: BestiaAiConfigChangedEvent) {
    events.publish(
      ZoneEvent.StanceChanged(
        event.accountId,
        event.masterId,
        event.playerBestiaId,
        event.config.stance.name,
        event.config.aggression,
      )
    )
  }

  /** Published by the compendium's own job, after its write committed. */
  @EventListener
  fun onCompendiumChanged(event: CompendiumChangedEvent) {
    events.publish(ZoneEvent.CompendiumChanged(event.accountId, event.masterId))
  }

  /** After the commit, so an app that refetches the map at once sees the new charts. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  fun onChartsChanged(event: ChartsChangedEvent) {
    jobs.submit(event.masterId) {
      val master = masters.findByIdOrNull(event.masterId) ?: return@submit
      events.publish(ZoneEvent.ChartsChanged(master.account.id, master.id))
    }
  }
}
