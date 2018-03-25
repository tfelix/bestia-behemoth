package net.bestia.zoneserver.script.api

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.whenever
import net.bestia.entity.EntityService
import net.bestia.entity.component.PositionComponent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.io.InputStreamReader
import javax.script.Compilable
import javax.script.Invocable
import javax.script.ScriptContext
import javax.script.ScriptEngineManager

@RunWith(MockitoJUnitRunner::class)
class EntityApiTest {

  private class TestFn : Runnable {
    override fun run() {
      println("Runnable from JS")
    }
  }

  @Mock
  private lateinit var entityService: EntityService

  @Mock
  private lateinit var positionComponent: PositionComponent

  fun testFn(): Runnable = Runnable { println("Runnable from JS") }

  @Before
  fun setup() {
    whenever(entityService.getComponentOrCreate(
            any<Long>(),
            eq(PositionComponent::class.java)))
            .thenReturn(positionComponent)
  }

  @Test
  fun test() {

    val engine = ScriptEngineManager().getEngineByName("nashorn")

    val bindings = engine.createBindings()
    bindings["Bestia"] = ScriptRootApi(entityService)
    engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE)

    val scriptFile = this.javaClass.classLoader.getResourceAsStream("script/test.js")
    val scriptReader = InputStreamReader(scriptFile)
    val script = (engine as Compilable).compile(scriptReader)
    script.eval()

    // engine.getBindings(ScriptContext.ENGINE_SCOPE)["bla"] = 5

    val invocable = script.engine as Invocable
    invocable.invokeFunction("main")
  }
}