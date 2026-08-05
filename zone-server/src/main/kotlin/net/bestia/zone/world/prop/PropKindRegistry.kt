package net.bestia.zone.world.prop

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/** A collider's half-extents and height, in position units. */
data class PropColliderDto(val halfX: Long = 1, val halfY: Long = 1, val height: Long = 2)

/** One roll of loot, `PropDeathDivergenceSystem`'s own image of `LootItemEntityFactory`'s bestia loot rows. */
data class PropLootEntryDto(val itemId: Long, val amount: Int = 1, val dropChance: Int = 10000)

data class PropKindDto(
  val kind: StaticEntityKind,
  val maxHp: Int,
  val collider: PropColliderDto = PropColliderDto(),
  val variants: Int = 1,
  /** Present means this kind regrows this many seconds after being depleted; absent means terminal. */
  val regrowSeconds: Long? = null,
  val loot: List<PropLootEntryDto> = emptyList()
)

data class PropKindsDto(val kinds: List<PropKindDto> = emptyList())

/**
 * What each [StaticEntityKind] is made of, from `prop-kinds.yml`.
 *
 * An in-memory registry rather than a table, on the distinction `AiProfileRegistry` and
 * `MovementProfileRegistry` already draw: nothing persists this and nothing references it by id, so it is
 * configuration read once at boot. `items.yml` and the mob YAML files become rows because other rows point at
 * them by id.
 *
 * **Fail-fast on a missing kind**, which is the whole reason this is a registry and not a `when` in the
 * factory. A kind added to the enum and forgotten here would otherwise place entities with a default health
 * and a default collider and look entirely healthy - the same failure `MobImporterBootRunner` refuses by
 * validating habitat names against `Biome` at import time.
 */
@Service
class PropKindRegistry {

  private val byKind = HashMap<StaticEntityKind, PropKindDto>()

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
      .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
      .build()

    val dto = ClassPathResource(RESOURCE).inputStream.use {
      mapper.readValue(it, PropKindsDto::class.java)
    }

    dto.kinds.forEach { kind ->
      require(byKind.put(kind.kind, kind) == null) { "$RESOURCE declares ${kind.kind} twice" }
      require(kind.maxHp > 0) { "${kind.kind} has max-hp ${kind.maxHp}" }
      require(kind.variants >= 1) { "${kind.kind} has variants ${kind.variants}" }
      require(kind.regrowSeconds == null || kind.regrowSeconds > 0) {
        "${kind.kind} has regrow-seconds ${kind.regrowSeconds}"
      }
      kind.loot.forEach {
        require(it.dropChance in 1..10_000) { "${kind.kind} has a loot entry with drop-chance ${it.dropChance}" }
        require(it.amount > 0) { "${kind.kind} has a loot entry with amount ${it.amount}" }
      }
    }

    val missing = StaticEntityKind.entries.filterNot { it in byKind }
    require(missing.isEmpty()) { "$RESOURCE describes no ${missing.joinToString()}" }

    LOG.info { "Loaded ${byKind.size} static entity kinds" }
  }

  fun of(kind: StaticEntityKind): PropKindDto =
    byKind[kind] ?: throw IllegalStateException("no prop kind registered for $kind")

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val RESOURCE = "prop-kinds.yml"
  }
}
