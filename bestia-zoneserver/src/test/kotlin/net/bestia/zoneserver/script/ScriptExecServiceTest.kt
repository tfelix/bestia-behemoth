package net.bestia.zoneserver.script

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.env.SimpleScriptEnv
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ScriptExecServiceTest {
  private val fileResolver = ClasspathScriptFileResolver("classpath:/script")

  @Mock
  private lateinit var mobFactory: MobFactory

  private lateinit var scriptExecService: ScriptExecService

  @BeforeEach
  fun setup() {
    scriptExecService = ScriptExecService(
        fileResolver = fileResolver,
        mobFactory = mobFactory,
        idGeneratorService = IdGenerator()
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
    whenever(mobFactory.build(any(), any())).thenReturn(Entity(1))
    scriptExecService.executeFunction(
        SimpleScriptEnv(),
        EXISTING_API_TEST_SCRIPT,
        "main"
    )

    verify(mobFactory).build("blob", Point(TEST_ARGUMENT_INT, TEST_ARGUMENT_INT))
  }

  companion object {
    private const val EXISTING_API_TEST_SCRIPT = "scriptServiceTest"
    private const val LOG_TEST_CALLSTR = "Hello World"
    private const val TEST_ARGUMENT_INT = 10L
  }
}

