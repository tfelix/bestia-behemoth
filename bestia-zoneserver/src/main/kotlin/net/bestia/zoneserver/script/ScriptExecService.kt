package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.api.ScriptRootApi
import net.bestia.zoneserver.script.api.ScriptRootContext
import net.bestia.zoneserver.script.env.ScriptEnv
import org.springframework.stereotype.Service
import java.io.InputStreamReader
import javax.script.*

private val LOG = KotlinLogging.logger { }

@Service
class ScriptExecService(
        private val fileResolver: ScriptFileResolver,
        private val mobFactory: MobFactory,
        private val idGeneratorService: IdGenerator
) {
  private val engine = ScriptEngineManager().getEngineByName("nashorn")

  fun executeFunction(env: ScriptEnv, scriptName: String, fnName: String) {
    LOG.trace { "Call $fnName() in $scriptName" }
    val bindings = setupEnvironment(env)
    try {
      val script = getCompiledScript(scriptName)
      script.eval()
      script.engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE)
      (script.engine as Invocable).invokeFunction(fnName)
    } catch (e: NoSuchMethodException) {
      LOG.error(e) { "Function $fnName is missing in script $scriptName" }
    } catch (e: ScriptException) {
      LOG.error(e) { "Error during script execution." }
    }
  }

  private fun setupEnvironment(env: ScriptEnv): Bindings {
    val bindings = engine.createBindings()
    bindings["BESTIA"] = makeRootApi()
    env.setupEnvironment(bindings)

    return bindings
  }

  private fun makeRootApi(): ScriptRootApi {
    return ScriptRootApi(
            idGeneratorService = idGeneratorService,
            mobFactory = mobFactory,
            scriptContext = ScriptRootContext()
    )
  }

  private fun getCompiledScript(scriptName: String): CompiledScript {
    val inputStream = fileResolver.getScriptInputStream(scriptName)
    val scriptReader = InputStreamReader(inputStream)

    return (engine as Compilable).compile(scriptReader)
  }
}