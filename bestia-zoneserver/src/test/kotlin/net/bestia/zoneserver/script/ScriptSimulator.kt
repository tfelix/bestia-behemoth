package net.bestia.zoneserver.script

import com.nhaarman.mockitokotlin2.mock
import net.bestia.zoneserver.Fixtures
import net.bestia.zoneserver.actor.entity.EntityRequestService
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.exec.ItemScriptExec
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * This class simulates a Bestia script execution environment and tests if a script executes
 * successfully.
 */
class ScriptSimulator {
  private val resolver = PathMatchingResourcePatternResolver()

  private val mobFactory: MobFactory = mock { }
  private val entityCollisionService: EntityCollisionService = mock { }
  private val entityRequestService: EntityRequestService = mock { }

  private val idGenerator = IdGenerator(Fixtures.zoneserverNodeConfig)
  private val interceptor = ScriptCommandProcessorTestInterceptor()

  private val cache = ScriptCache()
  private val compiler = JavascriptScriptCompiler()

  private val rootApiFactory = ScriptRootApiFactory(
      mobFactory = mobFactory,
      entityCollisionService = entityCollisionService,
      idGenerator = idGenerator,
      entityRequestService = entityRequestService
  )

  private val scriptService = ScriptService(
      scriptCache = cache,
      scriptCommandProcessor = interceptor,
      scriptRootApiFactory = rootApiFactory
  )

  fun testItemScript(scriptName: String) {
    val resource = resolver.getResources("classpath*:script/item/$scriptName.js").first()
    val script = compiler.compile(resource)
    val scriptKey = ScriptKeyBuilder.getScriptKey(ScriptType.ITEM, scriptName)
    cache.addScript(scriptKey, script)

    val exec = ItemScriptExec.Builder()
        .apply {
        }.build()

    scriptService.execute(exec)

    // Perform more tests against the script behavior.
    // call setup callbacks

    cache.clear()
  }
}