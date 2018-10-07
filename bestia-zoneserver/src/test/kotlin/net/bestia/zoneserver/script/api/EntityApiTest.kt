package net.bestia.zoneserver.script.api

import net.bestia.model.domain.Direction
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.factory.EntityFactory
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

  private var positionComponent = PositionComponent(
      entityId = 1,
      shape = Point(10, 10),
      facing = Direction.EAST,
      isSightBlocking = false
  )

  @Mock
  private lateinit var entityFactory: EntityFactory

  fun testFn(): Runnable = Runnable { println("Runnable from JS") }

  @Test
  fun benchmark() {
    val durationCompile = testCompile()
    val durationEval = testEval()

    println()
    println("Duration (compile): $durationCompile ms")
    println("Duration (eval): $durationEval ms")
  }

  private fun testCompile(): Long {
    val engine = ScriptEngineManager().getEngineByName("nashorn")
    val bindings = engine.createBindings()
    bindings["Bestia"] = ScriptRootApi(entityService, entityFactory)
    engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE)

    val scriptFile = this.javaClass.classLoader.getResourceAsStream("script/testCompile.js")
    val scriptReader = InputStreamReader(scriptFile)
    val compiledScript = (engine as Compilable).compile(scriptReader)
    compiledScript.eval()

    val start = System.currentTimeMillis()
    for(i in 0..BENCHMARK_RUNS) {
      engine.getBindings(ScriptContext.ENGINE_SCOPE)["bla"] = Math.random()
      val invocable = compiledScript.engine as Invocable
      invocable.invokeFunction("main")
    }
    val end = System.currentTimeMillis()
    val duration = end - start
    println("Compiled script took: $duration ms")

    return duration
  }

  private fun testEval(): Long {
    val engine = ScriptEngineManager().getEngineByName("nashorn")
    val bindings = engine.createBindings()
    bindings["Bestia"] = ScriptRootApi(entityService, entityFactory)
    engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE)

    val scriptFile = this.javaClass.classLoader.getResourceAsStream("script/testEval.js")
    val scriptReader = InputStreamReader(scriptFile)
    val compiledScript = (engine as Compilable).compile(scriptReader)

    val start = System.currentTimeMillis()
    for (i in 0..BENCHMARK_RUNS) {
      engine.getBindings(ScriptContext.ENGINE_SCOPE)["bla"] = Math.random()
      compiledScript.eval()
    }
    val end = System.currentTimeMillis()
    val duration = end - start
    println("Evaled script took: $duration ms")

    return duration
  }

  companion object {
    private const val BENCHMARK_RUNS = 5000
  }
}