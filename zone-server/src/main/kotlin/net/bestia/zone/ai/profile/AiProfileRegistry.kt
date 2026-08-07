package net.bestia.zone.ai.profile

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service

/**
 * Loads every AI archetype from the `classpath:ai/` folder into memory, keyed by identifier. This is pure
 * behaviour configuration (not JPA-persisted), so it is held in a plain in-memory map rather than a
 * database table.
 *
 * On load it fail-fast validates that every referenced goal and action actually exists in [BestiaDomain],
 * so a typo in a YAML archetype surfaces at boot instead of as a mob that mysteriously does nothing.
 * Validation used to check four things — goals, actions, consideration inputs and response curves — against
 * four Spring bean registries. Two of those concepts no longer exist in YAML at all now that priority
 * formulas live in Kotlin, and the remaining two resolve against the domain object directly, so there are no
 * registry beans left to inject.
 */
@Service
class AiProfileRegistry {

  private val profilesById = mutableMapOf<String, AiProfile>()

  @PostConstruct
  fun load() {
    // Case-insensitive enums let YAML archetypes use lowercase values.
    val objectMapper = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
      .build()
    val resolver = PathMatchingResourcePatternResolver()
    // "classpath*:" (not "classpath:") is required here: with a plain "classpath:" prefix Spring resolves
    // the wildcard root by taking the *first* "ai/" directory the classloader finds and globbing only inside
    // it. Test fixtures put further files under a nested `ai/` root, and if one of those wins the lookup
    // this silently loads zero profiles instead of throwing. "classpath*:" aggregates every matching root.
    val resources = resolver.getResources("classpath*:$CLASSPATH_FOLDER/*.yml")

    resources.forEach { resource ->
      val dto = objectMapper.readValue(resource.inputStream, AiProfileDto::class.java)
      register(dto)
    }

    LOG.info { "Loaded ${profilesById.size} AI profiles: ${profilesById.keys}" }
  }

  /**
   * Parses, fail-fast validates and stores a single profile. Exposed for the loader and for tests; throws
   * [IllegalArgumentException] if the profile references anything the domain does not define.
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
      require(actionId in BestiaDomain.ACTION_IDS) {
        "AI profile '${profile.identifier}' references unknown action '$actionId'; " +
          "known actions are ${BestiaDomain.ACTION_IDS.sorted()}"
      }
    }

    profile.goals.forEach { goal ->
      require(goal.name in BestiaDomain.Goals.BY_NAME) {
        "AI profile '${profile.identifier}' references unknown goal '${goal.name}'; " +
          "known goals are ${BestiaDomain.Goals.BY_NAME.keys.sorted()}"
      }
    }

    // A profile that names a goal but not the actions that could ever satisfy it produces a mob which
    // selects that goal, fails to plan, and retries forever. Cheap to catch here, maddening to debug live.
    require(profile.goals.isEmpty() || profile.actionIds.isNotEmpty()) {
      "AI profile '${profile.identifier}' declares goals but no actions, so it can never plan"
    }
  }

  companion object {
    private const val CLASSPATH_FOLDER = "ai"
    private val LOG = KotlinLogging.logger { }
  }
}
