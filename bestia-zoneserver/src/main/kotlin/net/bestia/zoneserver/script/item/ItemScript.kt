package net.bestia.zoneserver.script.item

import net.bestia.zoneserver.script.*
import net.bestia.zoneserver.script.api.BestiaApi
import org.springframework.stereotype.Component

@Component
interface ItemScript : Script {
    val itemDatabaseName: String

    override fun execute(api: BestiaApi, exec: ScriptExec) {
        when(exec) {
            is ItemEntityScriptExec -> executeItemOnEntity(api, exec)
            is ItemLocationScriptExec -> executeItemOnLocation(exec)
            else -> throw WrongScriptTypeException(exec, ItemScriptExec::class.java)
        }
    }

    fun executeItemOnEntity(api: BestiaApi, exec: ItemEntityScriptExec) { }

    fun executeItemOnLocation(exec: ItemLocationScriptExec) { }
}
