package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.entity.component.Component

interface ComponentMessage<out T : Component> {
  val componentType: Class<out T>
}