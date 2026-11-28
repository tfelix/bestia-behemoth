package net.bestia.zone.item.meal

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.InputStream

/**
 * `meals.yml`: every meal Cooking makes, what eating one restores, and what its
 * [net.bestia.zone.battle.status.scripts.Satisfied] grants while it lasts.
 *
 * The file is read at construction; its item names are resolved in [warmUp], after the importer wrote the item
 * table, so a typo fails the boot rather than the first meal.
 */
@Service
class MealCatalog(
  private val items: ItemTemplateRegistry,
) {

  /** One meal, with its item resolved. */
  data class Meal(
    val itemId: Long,
    val identifier: String,
    val durationSeconds: Int,
    val health: Int,
    val stamina: Int,
    val mana: Int,
    val bonus: MealBonus,
    val grants: List<Grant>,
  )

  /** Another status effect a meal lays on its eater, for its own duration unless [durationSeconds] says. */
  data class Grant(val effect: StatusEffectId, val level: Int, val durationSeconds: Int?)

  private lateinit var file: MealsFile

  /** How long eating any meal takes: the channel before it does anything. */
  val channelSeconds: Float get() = file.channelSeconds

  private val byItemId: Map<Long, Meal> by lazy {
    file.meals.associate { dto ->
      val itemId = requireNotNull(items.idOf(dto.meal)) { "$RESOURCE names ${dto.meal}, which items.yml does not have" }
      itemId to Meal(
        itemId = itemId,
        identifier = dto.meal,
        durationSeconds = dto.durationSeconds,
        health = dto.restore.health,
        stamina = dto.restore.stamina,
        mana = dto.restore.mana,
        bonus = dto.bonus,
        grants = dto.grants.map { Grant(it.effect, it.level, it.durationSeconds) },
      )
    }
  }

  @PostConstruct
  fun load() {
    ClassPathResource(RESOURCE).inputStream.use { loadFrom(it) }
    LOG.info { "Loaded ${file.meals.size} meals" }
  }

  /** Reads a `meals.yml` from [input]: the real one at boot, a small one in a test. */
  internal fun loadFrom(input: InputStream) {
    file = MAPPER.readValue(input, MealsFile::class.java)
    validate(file)
  }

  fun warmUp() {
    byItemId.size
  }

  /** Null for an item that is not a meal. */
  fun of(itemId: Long): Meal? = byItemId[itemId]

  fun isMeal(itemId: Long): Boolean = byItemId.containsKey(itemId)

  fun all(): Collection<Meal> = byItemId.values

  internal data class MealsFile(
    val channelSeconds: Float,
    val meals: List<MealDto> = emptyList(),
  )

  internal data class MealDto(
    val meal: String,
    val durationSeconds: Int,
    val restore: RestoreDto = RestoreDto(),
    val bonus: MealBonus = MealBonus(),
    val grants: List<GrantDto> = emptyList(),
  )

  internal data class RestoreDto(val health: Int = 0, val stamina: Int = 0, val mana: Int = 0)

  internal data class GrantDto(val effect: StatusEffectId, val level: Int = 1, val durationSeconds: Int? = null)

  companion object {
    private val LOG = KotlinLogging.logger { }
    const val RESOURCE = "meals.yml"

    private val MAPPER = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
      .build()

    internal fun validate(file: MealsFile) {
      require(file.channelSeconds > 0f) { "$RESOURCE: channel-seconds must be positive" }
      require(file.meals.isNotEmpty()) { "$RESOURCE names no meal" }

      val twice = file.meals.groupBy { it.meal }.filterValues { it.size > 1 }.keys
      require(twice.isEmpty()) { "$RESOURCE names these meals twice: $twice" }

      file.meals.forEach { meal ->
        require(meal.durationSeconds > 0) { "$RESOURCE: ${meal.meal} needs a positive duration-seconds" }
        require(meal.grants.none { it.effect == StatusEffectId.SATISFIED }) {
          "$RESOURCE: ${meal.meal} grants SATISFIED, which every meal lays on by itself"
        }
      }
    }
  }
}
