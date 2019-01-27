package net.bestia.zoneserver.script

import com.nhaarman.mockitokotlin2.verify
import net.bestia.zoneserver.entity.IdGeneratorService
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.api.ScriptRootApi
import net.bestia.zoneserver.script.env.SimpleScriptEnv
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ScriptExecServiceTest {
  @Mock
  private lateinit var api: ScriptRootApi

  @Mock
  private lateinit var fileResolver: ScriptFileResolver

  @Mock
  private lateinit var mobFactory: MobFactory

  private lateinit var scriptExecService: ScriptExecService

  @BeforeEach
  fun setup() {
    scriptExecService = ScriptExecService(
            fileResolver = fileResolver,
            mobFactory = mobFactory,
            idGeneratorService = IdGeneratorService()
    )
  }

  @Test
  fun `callScriptMainFunction() with unknown scriptname does nothing`() {
    assertThrows(IllegalArgumentException::class.java) {
      scriptExecService.executeFunction(
              SimpleScriptEnv(),
              "unknownscript",
              "unknown"
      )
    }
  }

  @Test
  fun `callScriptMainFunction() with known scriptname executes script`() {
    scriptExecService.executeFunction(
            SimpleScriptEnv(),
            EXISTING_API_TEST_SCRIPT,
            "main"
    )

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

