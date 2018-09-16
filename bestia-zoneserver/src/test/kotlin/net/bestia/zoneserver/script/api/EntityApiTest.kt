package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.entity.factory.MobFactory
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

  @Mock
  private lateinit var mobFactory: MobFactory

  fun testFn(): Runnable = Runnable { println("Runnable from JS") }

  @Before
  fun setup() {
    // whenever(entityService.getComponentOrCreate(any<Long>(), any(PositionComponent::class.java)).thenReturn(positionComponent)
  }

  @Test
  fun benchmark() {
    testCompile()
    testEval()
  }


  fun testCompile() {

    val engine = ScriptEngineManager().getEngineByName("nashorn")
    val bindings = engine.createBindings()
    bindings["Bestia"] = ScriptRootApi(entityService, mobFactory)
    engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE)

    val scriptFile = this.javaClass.classLoader.getResourceAsStream("script/testCompile.js")
    val scriptReader = InputStreamReader(scriptFile)
    val compiledScript = (engine as Compilable).compile(scriptReader)
    compiledScript.eval()

    var i = 0
    val start = System.currentTimeMillis()
    while (i < 500000) {
      engine.getBindings(ScriptContext.ENGINE_SCOPE)["bla"] = Math.random()
      val invocable = compiledScript.engine as Invocable
      invocable.invokeFunction("main")
      i++
    }
    val end = System.currentTimeMillis()
    val duration = end - start
    println("Compiled script took: $duration ms")
  }

  fun testEval() {

    val engine = ScriptEngineManager().getEngineByName("nashorn")
    val bindings = engine.createBindings()
    bindings["Bestia"] = ScriptRootApi(entityService, mobFactory)
    engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE)

    val scriptFile = this.javaClass.classLoader.getResourceAsStream("script/testEval.js")
    val scriptReader = InputStreamReader(scriptFile)
    val compiledScript = (engine as Compilable).compile(scriptReader)


    var i = 0
    val start = System.currentTimeMillis()
    while (i < 500000) {
      engine.getBindings(ScriptContext.ENGINE_SCOPE)["bla"] = Math.random()
      compiledScript.eval()
      i++
    }
    val end = System.currentTimeMillis()
    val duration = end - start
    println("Evaled script took: $duration ms")
  }
}