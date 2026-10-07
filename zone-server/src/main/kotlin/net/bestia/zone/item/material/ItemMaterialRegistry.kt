package net.bestia.zone.item.material

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.zone.battle.ElementModifier
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.util.EnumMap

/**
 * The [ItemMaterialSpec] of every [ItemMaterial], from `item-materials.yml`. Configuration read once at boot,
 * like `PropKindRegistry`: nothing persists it and no row points at it.
 */
@Service
class ItemMaterialRegistry {

  private val byMaterial = EnumMap<ItemMaterial, ItemMaterialSpec>(ItemMaterial::class.java)

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
      .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
      .build()

    val file = ClassPathResource(RESOURCE).inputStream.use {
      mapper.readValue(it, MaterialsFile::class.java)
    }

    file.materials.forEach { spec ->
      require(byMaterial.put(spec.material, spec) == null) { "$RESOURCE declares ${spec.material} twice" }
      require(spec.integrity > 0) { "${spec.material} has integrity ${spec.integrity}" }
      require(spec.hardness >= 0) { "${spec.material} has hardness ${spec.hardness}" }
      spec.elementMultipliers.forEach { (element, percent) ->
        require(ElementModifier.isLegalAttackElement(element)) { "${spec.material} weighs $element, not an attack element" }
        require(percent >= 0) { "${spec.material} has $percent% against $element" }
      }
    }

    val missing = ItemMaterial.entries.filterNot { it in byMaterial }
    require(missing.isEmpty()) { "$RESOURCE describes no ${missing.joinToString()}" }

    LOG.info { "Loaded ${byMaterial.size} item materials" }
  }

  fun of(material: ItemMaterial): ItemMaterialSpec {
    return byMaterial[material] ?: throw IllegalStateException("no spec registered for $material")
  }

  private data class MaterialsFile(val materials: List<ItemMaterialSpec> = emptyList())

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val RESOURCE = "item-materials.yml"
  }
}
