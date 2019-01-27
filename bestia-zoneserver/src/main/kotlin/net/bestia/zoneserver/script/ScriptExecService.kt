package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.entity.IdGeneratorService
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
        private val idGeneratorService: IdGeneratorService
) {
  private val engine = ScriptEngineManager().getEngineByName("nashorn")

  fun executeFunction(env: ScriptEnv, scriptName: String, fnName: String) {
    LOG.trace { "Call $fnName() in $scriptName" }
    val bindings = setupEnvironment(env)
    val script = getCompiledScript(scriptName)
    val inv = script.engine as Invocable
    try {
      script.eval(bindings)
      inv.invokeFunction(fnName)
    } catch (e: NoSuchMethodException) {
      LOG.error(e) { "Function $fnName is missing in script $script" }
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