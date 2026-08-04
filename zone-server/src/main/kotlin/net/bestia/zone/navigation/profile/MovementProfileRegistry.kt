package net.bestia.zone.navigation.profile

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.worldgen.core.MovementMode
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service

/**
 * Loads every movement profile from `classpath:movement/` into memory, keyed by identifier.
 *
 * The same shape as `AiProfileRegistry`, deliberately: behaviour configuration rather than persisted state,
 * so a plain map loaded at boot rather than a table. Keeping the two separate is the point - what a creature
 * *wants* is an AI profile and how it *gets there* is this, and a flying scout and a ground scout can share
 * every goal while disagreeing about rivers.
 */
@Service
class MovementProfileRegistry {

  private val profilesById = mutableMapOf<String, MovementProfile>()

  @PostConstruct
  fun load() {
    val objectMapper = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
      .build()

    val resolver = PathMatchingResourcePatternResolver()
    for (resource in resolver.getResources("classpath:$CLASSPATH_FOLDER/*.yml")) {
      val dto = objectMapper.readValue(resource.inputStream, MovementProfileDto::class.java)
      register(dto)
    }

    // Synthesised rather than shipped as a file, so that "what an unconfigured creature does" has exactly
    // one definition and it is the one in code that every caller already falls back to.
    if (MovementProfile.DEFAULT_IDENTIFIER !in profilesById) {
      profilesById[MovementProfile.DEFAULT_IDENTIFIER] = MovementProfile(
        identifier = MovementProfile.DEFAULT_IDENTIFIER,
        capabilities = setOf(MovementMode.WALK, MovementMode.CLIMB),
        agentHalfWidth = 0.5,
        roadCostMultiplier = 1.0,
        offRoadCostMultiplier = 1.0
      )
    }

    LOG.info { "Loaded ${profilesById.size} movement profiles: ${profilesById.keys}" }
  }

  fun register(dto: MovementProfileDto): MovementProfile {
    val profile = MovementProfile.fromDto(dto)
    profilesById[profile.identifier] = profile
    return profile
  }

  fun get(identifier: String): MovementProfile? = profilesById[identifier]

  /**
   * The named profile, or the default when it is unknown.
   *
   * Falling back rather than throwing, unlike `AiProfileRegistry.getOrThrow`, and the asymmetry is
   * deliberate: an unknown AI profile means a creature with no behaviour at all, which is worth refusing to
   * boot over. An unknown movement profile means a creature that walks like anything else, which is worth a
   * warning and a moving NPC rather than a dead one.
   */
  fun getOrDefault(identifier: String?): MovementProfile {
    if (identifier == null) return default()

    return profilesById[identifier] ?: run {
      if (warnedAbout.add(identifier)) {
        LOG.warn { "Unknown movement profile '$identifier'; falling back to ${MovementProfile.DEFAULT_IDENTIFIER}" }
      }
      default()
    }
  }

  fun default(): MovementProfile = profilesById.getValue(MovementProfile.DEFAULT_IDENTIFIER)

  fun all(): Collection<MovementProfile> = profilesById.values

  /** Identifiers already complained about, so one mistyped species does not log once per spawn. */
  private val warnedAbout = HashSet<String>()

  companion object {
    private const val CLASSPATH_FOLDER = "movement"
    private val LOG = KotlinLogging.logger { }
  }
}
