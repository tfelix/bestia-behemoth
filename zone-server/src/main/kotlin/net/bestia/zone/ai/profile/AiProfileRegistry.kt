package net.bestia.zone.ai.profile

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.zone.ai.goal.GoalRegistry
import net.bestia.zone.ai.goal.consideration.ConsiderationInputRegistry
import net.bestia.zone.ai.goal.consideration.CurveRegistry
import net.bestia.zone.ai.planner.GoapActionRegistry
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service

/**
 * Loads every AI archetype from the `classpath:ai/` folder into memory, keyed by identifier. This is pure
 * behaviour configuration (not JPA-persisted), so it is held in a plain in-memory map rather than a
 * database table.
 *
 * On load it fail-fast validates that every referenced goal, action, consideration input and
 * response curve actually resolves to a registered bean, so a typo in a YAML archetype surfaces at
 * boot instead of at runtime.
 */
@Service
class AiProfileRegistry(
  private val curveRegistry: CurveRegistry,
  private val inputRegistry: ConsiderationInputRegistry,
  private val goalRegistry: GoalRegistry,
  private val actionRegistry: GoapActionRegistry
) {

  private val profilesById = mutableMapOf<String, AiProfile>()

  @PostConstruct
  fun load() {
    // Case-insensitive enums let YAML archetypes use lowercase values (e.g. `combine: product`).
    val objectMapper = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
      .build()
    val resolver = PathMatchingResourcePatternResolver()
    val resources = resolver.getResources("classpath:$CLASSPATH_FOLDER/*.yml")

    resources.forEach { resource ->
      val dto = objectMapper.readValue(resource.inputStream, AiProfileDto::class.java)
      register(dto)
    }

    LOG.info { "Loaded ${profilesById.size} AI profiles: ${profilesById.keys}" }
  }

  /**
   * Parses, fail-fast validates and stores a single profile. Exposed for the loader and for tests;
   * throws [IllegalArgumentException] if the profile references anything unregistered.
   */
  fun register(dto: AiProfileDto): AiProfile {
    val profile = AiProfile.fromDto(dto)
    validate(profile)
    profilesById[profile.identifier] = profile
    return profile
  }

  fun get(identifier: String): AiProfile? = profilesById[identifier]

  fun getOrThrow(identifier: String): AiProfile =
    get(identifier) ?: throw IllegalArgumentException("Unknown AI profile '$identifier'")

  fun all(): Collection<AiProfile> = profilesById.values

  private fun validate(profile: AiProfile) {
    profile.actionIds.forEach { actionId ->
      require(actionRegistry.has(actionId)) {
        "AI profile '${profile.identifier}' references unknown action '$actionId'"
      }
    }

    profile.goals.forEach { goal ->
      require(goalRegistry.has(goal.name)) {
        "AI profile '${profile.identifier}' references unknown goal '${goal.name}'"
      }
      goal.considerations.forEach { consideration ->
        require(inputRegistry.has(consideration.input)) {
          "AI profile '${profile.identifier}' references unknown consideration input '${consideration.input}'"
        }
        require(curveRegistry.has(consideration.curve)) {
          "AI profile '${profile.identifier}' references unknown response curve '${consideration.curve}'"
        }
      }
    }
  }

  companion object {
    private const val CLASSPATH_FOLDER = "ai"
    private val LOG = KotlinLogging.logger { }
  }
}
