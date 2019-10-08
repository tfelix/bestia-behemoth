package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.OriginalStatusComponent
import net.bestia.zoneserver.entity.component.StatusComponent

/**
 * The actor checks an entity with a status component attached and will
 * periodically calculate (usually every second) the hp and mana regeneration.
 * It will then update the current Mana and current HP values.
 *
 * @author Thomas Felix
 */
@ActorComponent(StatusComponent::class)
class StatusComponentActor(
    component: StatusComponent
) : ComponentActor<StatusComponent>(component) {

  override fun createReceive(builder: ReceiveBuilder) {
    builder
        .match(OriginalStatusComponent::class.java, this::onOriginalStatusComponentChanged)
  }

  private fun onOriginalStatusComponentChanged(originalStatusComponent: OriginalStatusComponent) {
    // TODO Update the status values with the modified version via equip, scripts, buffs etc.
    component = component.copy(statusValues = originalStatusComponent.statusValues)
  }

  companion object {
    const val NAME = "statusComponent"
  }
}
