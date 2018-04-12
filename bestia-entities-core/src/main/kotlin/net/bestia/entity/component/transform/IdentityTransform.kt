package net.bestia.entity.component.transform

import net.bestia.entity.component.Component

/**
 * Performs no transformation.
 */
class IdentityTransform : SyncTransformer<Component> {
  override fun transform(component: Component): Component {
    return component
  }
}