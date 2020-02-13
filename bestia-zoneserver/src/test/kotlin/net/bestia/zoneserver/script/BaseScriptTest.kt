package net.bestia.zoneserver.script

import com.nhaarman.mockitokotlin2.mock
import net.bestia.zoneserver.Fixtures
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseScriptTest {
  protected val mobFactory: MobFactory = mock { }
  protected val entityCollisionService: EntityCollisionService = mock { }

  private val idGenerator = IdGenerator(Fixtures.zoneserverNodeConfig)
  protected val interceptor = ScriptCommandProcessorTestInterceptor()

  private val scriptProvider = ClasspathJavaScriptFileProvider()
  private val cache = ScriptCache()
  private val compiler = JavascriptScriptCompiler()
  private val rootApiFactory = ScriptRootApiFactory(
      mobFactory = mobFactory,
      entityCollisionService = entityCollisionService,
      idGenerator = idGenerator
  )

  private val bootStep = ScriptCompilerBootStep(
      fileProvider = scriptProvider,
      scriptCache = cache,
      scriptCompiler = compiler
  )

  protected val scriptService = ScriptService(
      scriptCache = cache,
      scriptCommandProcessor = interceptor,
      scriptRootApiFactory = rootApiFactory
  )

  @BeforeAll
  fun setup() {
    // This will compile all the scripts in the classpath
    bootStep.execute()
  }
}