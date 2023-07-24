package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.*
import org.springframework.stereotype.Component

@Component
class BestiaApiFactory(
    private val mobFactory: MobFactory,
    private val idGenerator: IdGenerator
) {

    fun buildScriptRootApi(exec: ScriptContext): BestiaApi {
        val scriptName = when(exec) {
            is ItemScriptContext -> "ITEM_" + exec.itemDatabaseName
            is AttackScriptContext -> "ATTACK_" + exec.attack.databaseName
        }

        return BestiaApi(
            idGeneratorService = idGenerator,
            mobFactory = mobFactory,
            scriptName = scriptName
        )
    }
}