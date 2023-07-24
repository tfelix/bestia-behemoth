package net.bestia.zoneserver.script.attack

import net.bestia.zoneserver.script.*
import net.bestia.zoneserver.script.api.BestiaApi
import net.bestia.zoneserver.script.Script
import org.springframework.stereotype.Component

@Component
interface AttackScript : Script {

    val attackDatabaseName: String

    override fun execute(api: BestiaApi, exec: ScriptExec) {
        when(exec) {
            is AttackEntityScriptExec -> executeAttackOnEntity(api, exec)
            is AttackLocationScriptExec -> executeAttackOnLocation(api, exec)
            else -> throw WrongScriptTypeException(exec, AttackScriptExec::class.java)
        }
    }

    fun executeAttackOnEntity(api: BestiaApi, exec: AttackEntityScriptExec) { }

    fun executeAttackOnLocation(api: BestiaApi, exec: AttackLocationScriptExec) { }
}