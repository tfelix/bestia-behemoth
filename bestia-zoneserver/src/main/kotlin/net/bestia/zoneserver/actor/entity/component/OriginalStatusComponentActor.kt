package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.SubscribeForComponentUpdates
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.OriginalStatusComponent
import net.bestia.zoneserver.status.GeneralOriginalStatusComponentFactory

/**
 * The actor checks an entity with a status component attached and will
 * periodically calculate (usually every second) the hp and mana regeneration.
 * It will then update the current Mana and current HP values.
 *
 * @author Thomas Felix
 */
@ActorComponent(OriginalStatusComponent::class)
class OriginalStatusComponentActor(
    originalStatusComponent: OriginalStatusComponent,
    private val originalStatusComponentFactory: GeneralOriginalStatusComponentFactory
) : ComponentActor<OriginalStatusComponent>(originalStatusComponent) {

  override fun preStart() {
    val subscribeLevelChange = SubscribeForComponentUpdates(LevelComponent::class.java, self)
    context.parent.tell(subscribeLevelChange, self)
  }

  override fun createReceive(builder: ReceiveBuilder) {
    builder
        .match(LevelComponent::class.java, this::onLevelComponentChanged)
  }

  private fun onLevelComponentChanged(levelComponent: LevelComponent) {
    requestOwnerEntity {
      component = originalStatusComponentFactory.buildComponent(it)
    }
  }

  companion object {
    const val NAME = "originalStatusComponent"
  }
}