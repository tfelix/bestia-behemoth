package net.bestia.entity.component.transform

import net.bestia.entity.component.Component

@org.springframework.stereotype.Component
interface SyncTransformer<T : Component> {
  fun transform(component: T): T
}