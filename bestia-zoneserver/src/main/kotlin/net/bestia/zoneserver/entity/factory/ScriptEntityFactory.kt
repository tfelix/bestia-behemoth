package net.bestia.zoneserver.entity.factory

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.component.ScriptComponent
import org.springframework.stereotype.Component

/**
 * This builds a script entity which can be used by scripts because it usually
 * has some collision detection which is used in order to perform some action
 * upon
 *
 * @author Thomas Felix
 */
@Component
class ScriptEntityFactory : AbstractFactory<ScriptBlueprint>(ScriptBlueprint::class.java) {

  override fun performBuild(entity: Entity, blueprint: ScriptBlueprint) {
    entity.addAllComponents(
        listOf(
            PositionComponent(
                entityId = entity.id,
                shape = blueprint.position
            ),
            ScriptComponent(
                entityId = entity.id
            ).also {
              it.addScriptCallback(
                  ScriptComponent.simpleCallback(blueprint.scriptName, blueprint.intervalMs)
              )
            }
        )
    )
  }
}
