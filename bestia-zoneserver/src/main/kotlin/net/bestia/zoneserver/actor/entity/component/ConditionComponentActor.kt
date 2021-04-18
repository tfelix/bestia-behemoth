package net.bestia.zoneserver.actor.entity.component

import akka.actor.ActorRef
import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.model.entity.StatusBasedValues
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.ComponentUpdated
import net.bestia.zoneserver.battle.RegenerationService
import net.bestia.zoneserver.entity.component.ConditionComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import java.time.Duration

sealed class ConditionCommand : ComponentMessage<ConditionComponent>, EntityMessage {
  override val componentType: Class<out ConditionComponent>
    get() = ConditionComponent::class.java
}

data class AddHp(override val entityId: Long, val hpDelta: Long) : ConditionCommand()
data class SetHp(override val entityId: Long, val hp: Long) : ConditionCommand()
data class AddMana(override val entityId: Long, val manaDelta: Long) : ConditionCommand()
data class SetMana(override val entityId: Long, val mana: Long) : ConditionCommand()

private val LOG = KotlinLogging.logger { }

@ActorComponent(ConditionComponent::class)
class ConditionComponentActor(
    conditionComponent: ConditionComponent,
    private val regenerationService: RegenerationService
) : ComponentActor<ConditionComponent>(conditionComponent) {

  private val currentIncrements = RegenerationService.ConditionIncrements()

  /**
   * We keep a reference to the status based values in order to increment them.
   */
  private var statusBasedValues: StatusBasedValues? = null

  private val tick = context.system.scheduler().scheduleAtFixedRate(
      REGEN_TICK_INTERVAL,
      REGEN_TICK_INTERVAL,
      self,
      ON_REGEN_TICK_MSG,
      context.dispatcher(),
      ActorRef.noSender()
  )

  override fun preStart() {
    createComponentUpdateSubscription(StatusComponent::class.java)
  }

  override fun createReceive(builder: ReceiveBuilder) {
    builder
        .matchEquals(ON_REGEN_TICK_MSG) { tickRegeneration() }
        .match(ConditionCommand::class.java, this::onConditionCommand)
        .match(ComponentUpdated::class.java, this::dependentComponentUpdated)
  }

  private fun onConditionCommand(cmd: ConditionCommand) {
    LOG.trace { "Received: $cmd" }

    val currentCondValues = component.conditionValues

    component = when (cmd) {
      is AddHp -> component.copy(conditionValues = currentCondValues.addHealth(cmd.hpDelta.toInt()))
      is SetHp -> component.copy(conditionValues = currentCondValues.copy(currentHealth = cmd.hp.toInt()))
      is AddMana -> component.copy(conditionValues = currentCondValues.addHealth(cmd.manaDelta.toInt()))
      is SetMana -> component.copy(conditionValues = currentCondValues.copy(currentMana = cmd.mana.toInt()))
    }
  }

  private fun dependentComponentUpdated(componentUpdated: ComponentUpdated<*>) {
    when (val c = componentUpdated.component) {
      is StatusComponent -> statusBasedValues = c.statusBasedValues
    }
  }

  private fun tickRegeneration() {
    statusBasedValues?.let {
      val conditionValues = regenerationService.addIncrements(
          component.conditionValues,
          it,
          currentIncrements
      )
      component = component.copy(conditionValues = conditionValues)
    }
  }

  override fun postStop() {
    tick.cancel()
  }

  companion object {
    private val REGEN_TICK_INTERVAL = Duration.ofMillis(RegenerationService.REGENERATION_TICK_RATE_MS)
    const val ON_REGEN_TICK_MSG = "tickStatus"
  }
}
