package net.bestia.zoneserver.script.attack

import mu.KotlinLogging
import net.bestia.model.geometry.Cube
import net.bestia.zoneserver.script.AttackLocationScriptExec
import net.bestia.zoneserver.script.CallbackContext
import net.bestia.zoneserver.script.api.BestiaApi
import net.bestia.zoneserver.script.ScriptExec
import kotlin.reflect.jvm.javaMethod

private val LOG = KotlinLogging.logger { }

class FirePillarScript : AttackScript {
    override val attackDatabaseName = "fire_pillar"
    fun onTick(api: BestiaApi, exec: ScriptExec) {
        if(exec.callbackContext?.getCallbackType() == CallbackContext.Type.ON_TICK) {
            LOG.info { "Hello World" }

            // Get all Bestias in Range of fire_pillar
            val origin = api.self().getPosition()
            val box = DIMENSION.moveTo(origin)

            api.findEntities2(box, ::foundEntities.javaMethod)
        }
    }

    private fun foundEntities(api: BestiaApi, exec: ScriptExec, entityIds: Set<Long>) {
        LOG.info { "Callback with entitiesIds: $entityIds" }
        // Make fire Damage
    }

    override fun executeAttackOnLocation(api: BestiaApi, exec: AttackLocationScriptExec) {
        api.newEntity()
            .setLivetime(12000)
            .script()
            .setInterval(1000, ::onTick)
    }

    companion object {
        private val DIMENSION = Cube(3,1, 3)
    }
}