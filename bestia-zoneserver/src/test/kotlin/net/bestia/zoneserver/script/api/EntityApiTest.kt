package net.bestia.zoneserver.script.api

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.bestia.Direction
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGeneratorService
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.factory.MobFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.junit.jupiter.MockitoExtension
import java.io.InputStreamReader
import javax.script.Compilable
import javax.script.Invocable
import javax.script.ScriptContext
import javax.script.ScriptEngineManager

@ExtendWith(MockitoExtension::class)
class EntityApiTest {
  private val idGeneratorService = IdGeneratorService()

  @Mock
  private lateinit var mobFactory: MobFactory

  @BeforeEach
  fun setup() {
    whenever(mobFactory.build(any(), any())).thenReturn(Entity(1))
  }

  @Test
  fun benchmark() {
    val durationCompile = testCompile()
    val durationEval = testEval()

    println()
    println("Duration (compile): $durationCompile ms (${durationCompile.toFloat() / BENCHMARK_RUNS} ms/ea)")
    println("Duration (eval): $durationEval ms (${durationEval.toFloat() / BENCHMARK_RUNS} ms/ea)")
  }

  private fun testCompile(): Long {
    val engine = ScriptEngineManager().getEngineByName("nashorn")
    val bindings = engine.createBindings()
    val scriptRootContext = ScriptRootContext()
    bindings["Bestia"] = ScriptRootApi(idGeneratorService, mobFactory, scriptRootContext)
    engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE)

    val scriptFile = this.javaClass.classLoader.getResourceAsStream("script/benchmark/testCompile.js")
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
    val scriptRootContext = ScriptRootContext()
    bindings["Bestia"] = ScriptRootApi(idGeneratorService, mobFactory, scriptRootContext)
    engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE)

    val scriptFile = this.javaClass.classLoader.getResourceAsStream("script/benchmark/testEval.js")
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
    private const val BENCHMARK_RUNS = 10000
  }
}