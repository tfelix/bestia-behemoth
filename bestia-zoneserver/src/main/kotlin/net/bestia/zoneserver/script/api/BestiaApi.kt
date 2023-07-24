package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.model.geometry.Shape
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.entity.NewEntity
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.*
import java.lang.reflect.Method

private val LOG = KotlinLogging.logger { }

typealias FindEnititesCallback = (api: BestiaApi, exec: ScriptExec, entityIds: Set<Long>) -> Unit

/**
 * Global script API used by all scripts in the Bestia system to interact with
 * the Behemoth server.
 *
 * @author Thomas Felix
 */
class BestiaApi(
    val scriptName: String,
    private val idGeneratorService: IdGenerator,
    private val mobFactory: MobFactory,
    private val scriptContext: ScriptContext
) {
    private class MethodNameExtractor {

        fun extractMethodName(data: String): String {
            val match = NAME_REGEX.find(data)
                ?: throw BestiaScriptException("Method name could not be extracted from '$data'")

            return match.groupValues[1]
        }

        companion object {
            private val NAME_REGEX = """\.(\w+)\(""".toRegex()
        }
    }

    val messages = mutableListOf<ScriptMessage>()
    private val methodExtractor = MethodNameExtractor()

    fun info(text: Any) {
        LOG.info { "${scriptName}: $text" }
    }

    fun debug(text: Any) {
        LOG.debug { "${scriptName}: $text" }
    }

    fun findEntity(entityId: Long): EntityApi {
        LOG.trace { "${scriptName}: findEntity($entityId)" }
        require(entityId > 0L) { "Entity ID can not be null" }

        return EntityApi(
            entityId = entityId,
            commands = messages,
            scriptName = scriptName,
            entityRequestService = entityRequestService
        )
    }

    fun spawnMob(
        mobName: String,
        pos: Vec3
    ): EntityApi {
        LOG.trace { "spawnMob: $mobName pos: $pos" }
        require(pos.x > 0L) { "X must be greater then 0" }
        require(pos.y > 0L) { "Y must be greater then 0" }
        require(pos.z > 0L) { "Z must be greater then 0" }

        val entity = mobFactory.build(mobName, pos)
        messages.add(NewEntity(entity))

        return EntityApi(
            entityId = entity.id,
            commands = messages,
            scriptName = scriptName,
            entityRequestService = entityRequestService
        )
    }

    fun findEntities(shape: Shape): List<EntityApi> {
        LOG.trace { "${scriptName}: findEntities($shape)" }

        val entities = entityCollisionService.getAllCollidingEntityIds(shape)

        return entities.map {
            EntityApi(
                entityId = it,
                commands = messages,
                scriptName = scriptName,
                entityRequestService = entityRequestService
            )
        }
    }

    fun findEntities2(
        shape: Shape,
        callbackFn: FindEnititesCallback
    ) {
        LOG.trace { "${scriptName}: findEntities($shape)" }

        val methodName = methodExtractor.extractMethodName(callbackFn.toString())
        val callbackContext = CallbackContext(
            methodName = methodName,
            scriptContext = scriptContext
        )

        messages.add(EntitiesByShapeQuery(shape, callbackContext))
    }

    /**
     * Generic entity spawner that creates an empty entity that the script can setup.
     */
    fun newEntity(): EntityApi {
        LOG.trace { "${scriptName}: newEntity" }

        val entityId = idGeneratorService.newId()
        messages.add(NewEntity(Entity(entityId)))

        return EntityApi(
            entityId = entityId,
            commands = messages,
            scriptName = scriptName,
            entityRequestService = entityRequestService
        )
    }

    override fun toString(): String {
        return this::class.java.simpleName
    }

    fun self(): EntityApi {
        TODO("Not yet implemented")
    }

    fun findEntities2(shape: Shape, callbackFn: Method?) {
        LOG.info { callbackFn }
    }
}
