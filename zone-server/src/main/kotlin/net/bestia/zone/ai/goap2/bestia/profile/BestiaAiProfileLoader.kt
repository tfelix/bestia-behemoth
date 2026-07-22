package net.bestia.zone.ai.goap2.bestia.profile

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import net.bestia.zone.ai.goap2.bestia.BestiaDomain
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.io.InputStream

/**
 * Parses `ai/goap2/<name>.yml` archetypes into [BestiaAiProfile]s. Deliberately a plain class (no
 * `@Service`/`@PostConstruct`, unlike [net.bestia.zone.ai.profile.AiProfileRegistry]) — goap2 stays
 * Spring-free for now, so a caller decides when and how often to (re)load.
 */
class BestiaAiProfileLoader {

  private val mapper = JsonMapper.builder(YAMLFactory()).addModule(kotlinModule()).build()

  fun parse(yaml: InputStream): BestiaAiProfile =
    BestiaAiProfile.fromDto(mapper.readValue(yaml, BestiaAiProfileDto::class.java))

  fun parse(yaml: String): BestiaAiProfile =
    BestiaAiProfile.fromDto(mapper.readValue(yaml, BestiaAiProfileDto::class.java))

  /** Loads every archetype yml under the `ai/goap2` classpath folder, fail-fast validating goal/action ids. */
  fun loadAll(): List<BestiaAiProfile> {
    val resolver = PathMatchingResourcePatternResolver()
    return resolver.getResources("classpath:$CLASSPATH_FOLDER/*.yml").map { resource ->
      parse(resource.inputStream).also(::validate)
    }
  }

  private fun validate(profile: BestiaAiProfile) {
    val knownGoals = BestiaDomain.Goals.ALL.map { it.name }.toSet()
    profile.goals.forEach { goal ->
      require(goal.name in knownGoals) {
        "AI profile '${profile.identifier}' references unknown goal '${goal.name}'"
      }
    }

    val knownActions = BestiaDomain.actionTemplates().keys
    profile.actionIds.forEach { actionId ->
      require(actionId in knownActions) {
        "AI profile '${profile.identifier}' references unknown action '$actionId'"
      }
    }
  }

  companion object {
    private const val CLASSPATH_FOLDER = "ai/goap2"
  }
}
