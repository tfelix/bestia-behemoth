package net.bestia.zoneserver.entity.component

import net.bestia.zoneserver.entity.Entity

interface ComponentFactory<out T : Component> {
  fun buildComponent(entity: Entity): T
}