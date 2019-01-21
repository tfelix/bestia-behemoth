package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.battle.StatusService
import net.bestia.zoneserver.entity.component.StatusComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

/**
 * The actor checks an entity with a status component attached and will
 * periodically calculate (usually every second) the hp and mana regeneration.
 * It will then update the current Mana and current HP values.
 *
 * @author Thomas Felix
 */
@ActorComponent
@HandlesComponent(StatusComponent::class)
class StatusComponentActor(
    private val statusService: StatusService,
    component: StatusComponent
) : ComponentActor<StatusComponent>(component) {

  private val tick = context.system.scheduler().schedule(
      TICK_INTERVAL,
      TICK_INTERVAL,
      self,
      ON_TICK_MSG,
      context.dispatcher(),
      null
  )

  private var manaIncrement: Float = 0.toFloat()
  private var healthIncrement: Float = 0.toFloat()

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(ON_TICK_MSG) { onTick() }
  }

  private fun onTick() {
    awaitEntity { entity ->
      try {
        healthIncrement += statusService.getHealthTick(entity)
        manaIncrement += statusService.getManaTick(entity)

        val condValues = entity.getComponent(StatusComponent::class.java).conditionValues
        if (healthIncrement > 1) {
          val hpRound = healthIncrement.toInt()
          healthIncrement -= hpRound.toFloat()
          condValues.addHealth(hpRound)
        }

        if (manaIncrement > 1) {
          val manaRound = manaIncrement.toInt()
          manaIncrement -= manaRound.toFloat()
          condValues.addMana(manaRound)
        }
      } catch (e: IllegalArgumentException) {
        // Could not tick regeneration for this entity id.
        // Probably no status component attached.
        context().stop(self)
      }
    }
  }

  override fun postStop() {
    tick.cancel()
  }

  companion object {
    private val TICK_INTERVAL = Duration.create(
        StatusService.REGENERATION_TICK_RATE_MS.toLong(),
        TimeUnit.MILLISECONDS
    )

    private const val ON_TICK_MSG = "tickStatus"
    const val NAME = "statusComponent"
  }
}
