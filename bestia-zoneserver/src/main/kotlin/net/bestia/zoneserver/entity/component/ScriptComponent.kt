package net.bestia.zoneserver.entity.component

import java.util.*

/**
 * Holds various script callbacks for entities.
 *
 * @author Thomas Felix
 */
data class ScriptComponent(
    override val entityId: Long
) : Component {

  /**
   * Callback types of the different scripts. This is used to register
   * different callbacks for different hooks in the bestia engine. For further
   * information which variables are available for the different script
   * environments consult the various script environment implementations in
   * the bestia-zone module.
   */
  enum class TriggerType {

    /**
     * Script is called on a regular time basis.
     */
    ON_INTERVAL,

    /**
     * Script gets called for every entity entering the area.
     */
    ON_ENTER_AREA,

    /**
     * Script gets called for every entity leaving the area.
     */
    ON_LEAVE_AREA,

    /**
     * Script is called if the entity is damage awarded.
     */
    ON_TAKE_DMG,

    /**
     * This hook gets called before the damage is calculated to the script
     * so the script can influence the damage calculation.
     */
    ON_BEFORE_TAKE_DMG,

    /**
     * The script is called if a attack is about to be processed.
     */
    ON_ATTACK,

    /**
     * Script is called if an item is picked up by a player in the surrounding area.
     */
    ON_ITEM_PICKUP,

    /**
     * Script is called if a player drops an item in the surrounding area.
     */
    ON_ITEM_DROP
  }

  data class ScriptCallback(
      val uuid: String,
      val type: TriggerType,
      val script: String,
      val intervalMs: Int
  )

  val scripts = mutableMapOf<String, ScriptCallback>()

  fun addScriptCallback(callback: ScriptCallback) {
    scripts[callback.uuid] = callback
  }

  companion object {
    fun simpleCallback(scriptName: String, intervalMs: Int): ScriptCallback {
      return ScriptCallback(
          UUID.randomUUID().toString(),
          TriggerType.ON_INTERVAL,
          scriptName,
          intervalMs
      )
    }
  }
}