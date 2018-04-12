package net.bestia.entity.component.transformer

import net.bestia.entity.component.LevelComponent
import net.bestia.entity.component.transform.SyncTransformer

class LevelOnlyTransformer: SyncTransformer<LevelComponent> {
  override fun transform(component: LevelComponent): LevelComponent {
    return LevelComponent(component.id).also { it.level = component.level }
  }
}