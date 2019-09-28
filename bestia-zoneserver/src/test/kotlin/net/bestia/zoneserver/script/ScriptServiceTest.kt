package net.bestia.zoneserver.script

import com.nhaarman.mockitokotlin2.verify
import net.bestia.zoneserver.config.ZoneserverNodeConfig
import net.bestia.zoneserver.script.api.ScriptRootApi
import net.bestia.zoneserver.script.env.GlobalEnv
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ScriptServiceTest {
  private lateinit var scriptService: ScriptService

  @Mock
  private lateinit var api: ScriptRootApi

  @Mock
  private lateinit var scriptExecService: ScriptExecService

  private val zonserverConfig = ZoneserverNodeConfig(
      scriptDir = "classpath:script",
      serverName = "test",
      serverVersion = "1.0.0",
      websocketPort = 1337
  )
  private lateinit var globalEnv: GlobalEnv

  @BeforeEach
  fun setup() {
    globalEnv = GlobalEnv(api, zonserverConfig)
    scriptService = ScriptService(scriptExecService)
  }

  @Test
  fun `call main() function from unknown script does nothing`() {
    scriptService.callScriptMainFunction("unknownscript")
  }

  @Disabled("This crashes the JVM")
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

