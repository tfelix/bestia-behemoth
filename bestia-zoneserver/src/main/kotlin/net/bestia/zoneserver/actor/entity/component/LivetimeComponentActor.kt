package net.bestia.zoneserver.actor.entity.component

import akka.actor.*
import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.KillEntity
import net.bestia.zoneserver.entity.component.*
import java.time.Duration
import java.time.Instant

private val LOG = KotlinLogging.logger { }

@ActorComponent(LivetimeComponent::class)
class LivetimeComponentActor(
    livetimeComponent: LivetimeComponent
) : ComponentActor<LivetimeComponent>(livetimeComponent) {

  private var tick: Cancellable? = null

  override fun createReceive(builder: ReceiveBuilder) {
    builder
        .matchEquals(KILL_MESSAGE) { killEntity() }
  }

  override fun preStart() {
    setupKillTimer()
  }

  override fun onComponentChanged(oldComponent: LivetimeComponent, newComponent: LivetimeComponent) {
    setupKillTimer()
  }

  /**
   * Setup a new movement tick based on the delay. If the delay is negative we
   * know that we can not move and thus end the movement and this actor.
   */
  private fun setupKillTimer() {
    val delayMs = Duration.between(Instant.now(), component.killOn).toMillis()

    if (delayMs < 0) {
      killEntity()
      return
    }

    tick?.cancel()

    val scheduler = context.system().scheduler()
    tick = scheduler.scheduleOnce(Duration.ofMillis(delayMs),
        self, KILL_MESSAGE, context.dispatcher(), null)
  }

  override fun postStop() {
    tick?.cancel()
  }

  private fun killEntity() {
    LOG.debug { "kill entity: livetime is over" }
    context.parent.tell(KillEntity(entityId), self)
  }

  companion object {
    const val NAME = "livetimeComponent"
    private const val KILL_MESSAGE = "killEntity"
  }
}
