package net.bestia.zoneserver.script

import com.nhaarman.mockitokotlin2.verify
import net.bestia.zoneserver.config.ZoneserverConfig
import net.bestia.zoneserver.script.api.ScriptRootApi
import net.bestia.zoneserver.script.env.GlobalEnv
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ScriptServiceTest {
  private lateinit var scriptCache: ScriptCache

  @Mock
  private lateinit var api: ScriptRootApi

  private val zonserverConfig = ZoneserverConfig(
      scriptDir = "classpath:script",
      serverName = "test",
      serverVersion = "1.0.0",
      websocketPort = 1337
  )
  private lateinit var globalEnv: GlobalEnv
  private lateinit var scriptCompiler: ScriptCompiler
  private lateinit var scriptService: ScriptService

  @BeforeEach
  fun setup() {
    globalEnv = GlobalEnv(api, zonserverConfig)
    scriptCompiler = ScriptCompiler(globalEnv)

    scriptCache = ScriptCache(
        scriptCompiler,
        ClasspathScriptFileResolver(zonserverConfig.scriptDir)
    )

    scriptService = ScriptService(scriptCache)
  }

  @Test
  fun `callScriptMainFunction() with unknown scriptname does nothing`() {
    assertThrows(IllegalArgumentException::class.java) {
      scriptService.callScriptMainFunction("unknownscript")
    }
  }

  @Test
  fun `callScriptMainFunction() with known scriptname executes script`() {
    scriptService.callScriptMainFunction(EXISTING_API_TEST_SCRIPT)

    verify(api).debug(LOG_TEST_CALLSTR)
    verify(api).info(LOG_TEST_CALLSTR)
    verify(api).newEntity()
    verify(api).findEntity(TEST_ARGUMENT_INT)
    verify(api).spawnMob("blob", TEST_ARGUMENT_INT, TEST_ARGUMENT_INT)
  }

  companion object {
    private const val EXISTING_API_TEST_SCRIPT = "scriptServiceTest"
    private const val LOG_TEST_CALLSTR = "Hello World"
    private const val TEST_ARGUMENT_INT = 10L
  }
}

