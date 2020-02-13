package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.api.ScriptRootApi
import net.bestia.zoneserver.script.exec.ScriptExec
import org.springframework.stereotype.Component
import javax.script.Bindings

private val LOG = KotlinLogging.logger { }

@Component
class ScriptRootApiFactory(
    private val mobFactory: MobFactory,
    private val idGenerator: IdGenerator,
    private val entityCollisionService: EntityCollisionService
) {

  fun buildScriptRootApi(bindings: Bindings, exec: ScriptExec): ScriptRootApi {
    LOG.trace { "Building ScriptRoot for ${exec.scriptKey} [${exec.javaClass.simpleName}]" }
    exec.setupEnvironment(bindings)

    val rootApi = ScriptRootApi(
        scriptName = exec.scriptKey,
        idGeneratorService = idGenerator,
        mobFactory = mobFactory,
        entityCollisionService = entityCollisionService
    )

    bindings["Bestia"] = rootApi

    return rootApi
  }
}