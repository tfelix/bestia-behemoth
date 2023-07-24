package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.script.item.ItemScript
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
class ScriptCache(
    itemScripts: List<ItemScript>
) {

  private val scriptInstances: Map<String, Script>

  init {
    LOG.info { "Found ${itemScripts.size}" }

    scriptInstances = itemScripts.associateBy { PREFIX_ITEM_SCRIPT + it.itemDatabaseName }
  }

  fun getScriptInstance(exec: ScriptContext): Script {
    val scriptIdentifier = when(exec) {
      is ItemScriptContext -> PREFIX_ITEM_SCRIPT + exec.itemDatabaseName
      is AttackScriptContext -> PREFIX_ATTACK_SCRIPT + exec.attack.databaseName
    }

    return scriptInstances[scriptIdentifier]
        ?: throw BestiaScriptException("Script instance with identifier '$scriptIdentifier' not found")
  }

  companion object {
    private const val PREFIX_ITEM_SCRIPT = "item-"
    private const val PREFIX_ATTACK_SCRIPT = "attack-"
  }
}