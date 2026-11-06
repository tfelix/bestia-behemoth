package net.bestia.zone.prop

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import net.bestia.zone.entity.GrowthStage
import net.bestia.zone.entity.StaticEntityKind

/** A collider's half-extents and height, in position units. */
data class PropColliderDto(val halfX: Long = 1, val halfY: Long = 1, val height: Long = 2)

/** One roll of loot, `PropDeathDivergenceSystem`'s own image of `MobLootSpawner`'s bestia loot rows. */
data class PropLootEntryDto(val itemId: Long, val amount: Int = 1, val dropChance: Int = 10000)

/**
 * What one click yields, for a kind a player picks up rather than fights.
 *
 * Deliberately not a `PropLootEntryDto`: loot is a chance-rolled list that becomes ground stacks when a prop
 * is killed, and this is a single deterministic grant straight into the inventory. Sharing the type would
 * force one of the two to carry a mode flag.
 *
 * `itemId` is not validated against the item catalogue - see [PropKindRegistry.load].
 */
data class PropCollectDto(val itemId: Long, val amount: Int = 1)

/**
 * One stage of a tree growing back, against the grown tree.
 *
 * @property size its share of the grown tree's height and max HP
 * @property yield its share of the grown tree's loot amount, rounded down. A sapling is too thin for a log.
 */
data class PropGrowthStageDto(val size: Double, val yield: Double)

/**
 * How a removed tree comes back: as a stump, which then tries every [stageSeconds] to sprout and grows back
 * through [sapling] and [young]. It only sprouts while a grown tree stands within [neighbourRadius], so a wood
 * that was cleared completely stays cleared.
 */
data class PropRegrowthDto(
  val stageSeconds: Long,
  val neighbourRadius: Long,
  val sapling: PropGrowthStageDto,
  val young: PropGrowthStageDto
) {

  /** Share of the grown tree's height and max HP. A stump is drawn from the grown tree's height. */
  fun sizeOf(stage: GrowthStage): Double = when (stage) {
    GrowthStage.SAPLING -> sapling.size
    GrowthStage.YOUNG -> young.size
    GrowthStage.GROWN, GrowthStage.STUMP -> 1.0
  }

  /** Share of the grown tree's loot amount. */
  fun yieldOf(stage: GrowthStage): Double = when (stage) {
    GrowthStage.SAPLING -> sapling.yield
    GrowthStage.YOUNG -> young.yield
    GrowthStage.GROWN -> 1.0
    GrowthStage.STUMP -> 0.0
  }
}

data class PropKindDto(
  val kind: StaticEntityKind,
  val maxHp: Int,
  val collider: PropColliderDto = PropColliderDto(),
  val variants: Int = 1,
  /** Present means this kind regrows this many seconds after being depleted; absent means terminal. */
  val regrowSeconds: Long? = null,
  /** Present means this kind leaves a stump and grows back in stages instead. Never together with [regrowSeconds]. */
  val regrowth: PropRegrowthDto? = null,
  val loot: List<PropLootEntryDto> = emptyList(),
  /**
   * Present means this kind is taken with a click rather than by being felled; absent means it is not.
   *
   * The presence of the block **is** the rule, on the same shape as [regrowSeconds] - a per-kind property
   * written only where it applies. So a kind added to the enum is non-collectible by construction, rather
   * than by an exclusion list somewhere that a new kind can be forgotten from.
   */
  val collect: PropCollectDto? = null
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
 *
 * **But not on a `collect` item-id that names nothing.** This loads at `@PostConstruct`, and
 * `ItemImporterBootRunner` is an `@Order(100)` `CommandLineRunner` - so at the moment this runs, `items.yml`
 * has not been written to the `item` table yet and there is nothing to check against. An unknown id therefore
 * surfaces at collect time, where `ObtainItemIntentSystem` already logs it. Moving the check here would need
 * this to become a boot runner ordered after that one, which is a real cost for a typo that fails loudly the
 * first time anyone clicks the prop.
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
      kind.regrowth?.let { validateRegrowth(kind.kind, kind.regrowSeconds, it) }
      kind.loot.forEach {
        require(it.dropChance in 1..10_000) { "${kind.kind} has a loot entry with drop-chance ${it.dropChance}" }
        require(it.amount > 0) { "${kind.kind} has a loot entry with amount ${it.amount}" }
      }
      kind.collect?.let {
        require(it.itemId > 0) { "${kind.kind} has a collect entry with item-id ${it.itemId}" }
        require(it.amount > 0) { "${kind.kind} has a collect entry with amount ${it.amount}" }
      }
    }

    val missing = StaticEntityKind.entries.filterNot { it in byKind }
    require(missing.isEmpty()) { "$RESOURCE describes no ${missing.joinToString()}" }

    LOG.info { "Loaded ${byKind.size} static entity kinds" }
  }

  fun of(kind: StaticEntityKind): PropKindDto =
    byKind[kind] ?: throw IllegalStateException("no prop kind registered for $kind")

  private fun validateRegrowth(kind: StaticEntityKind, regrowSeconds: Long?, regrowth: PropRegrowthDto) {
    require(regrowSeconds == null) { "$kind has both regrow-seconds and regrowth" }
    require(regrowth.stageSeconds > 0) { "$kind has regrowth stage-seconds ${regrowth.stageSeconds}" }
    require(regrowth.neighbourRadius > 0) { "$kind has regrowth neighbour-radius ${regrowth.neighbourRadius}" }
    for ((name, stage) in listOf("sapling" to regrowth.sapling, "young" to regrowth.young)) {
      require(stage.size > 0.0 && stage.size < 1.0) { "$kind has a $name size of ${stage.size}" }
      require(stage.yield in 0.0..1.0) { "$kind has a $name yield of ${stage.yield}" }
    }
    require(regrowth.sapling.size < regrowth.young.size) { "$kind has a sapling no smaller than its young tree" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val RESOURCE = "prop-kinds.yml"
  }
}
