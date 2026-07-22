import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Cross-checks (and optionally fixes) the server item catalog (`zone-server/src/main/resources/items.yml`)
 * against the Godot client's Item DB (the `.tres` files under `bestia-client/src/Game/Item/DB/`),
 * the exact check/sync split [SkillDbSyncTask] uses for skills.
 *
 * `item_id`, `type`, `weight`, `equip_slot`, `name_key` and `description_key` are touched - every
 * other field (`icon`, `item_script`, `item_visual`) is hand-authored client presentation with no
 * server equivalent and is left alone. `equip_slot` is only checked/patched for `EQUIP` items;
 * anything else is fine relying on the resource default of 0 ("not equipment").
 *
 * `name_key`/`description_key` are derived from `items.yml`'s `item-db-name` identifier
 * (uppercased), not the numeric id - e.g. `jelly` -> `name_key = "JELLY"`,
 * `description_key = "JELLY_DESC"`. This keeps the localization keys stable across id
 * renumbering and readable in `items.csv`.
 *
 * `description_key` points into `bestia-client/src/Localization/items.csv`, a Godot CSV translation
 * source. Whenever `items.yml` declares a `description`, that text is synced into the CSV's `en`
 * column - the *only* column this task ever writes. Name rows (`<IDENTIFIER>`) get a placeholder
 * when missing and are then hand-authored, since the server has no display name for an item.
 *
 * `equip_slot` values are `EquipmentSlot` **ordinals + 1** (0 means "none"), matching
 * `Game/Item/equipment_slot.gd`. See [SLOT_ORDER].
 */
abstract class ItemDbSyncTask : DefaultTask() {

  @get:InputFile
  abstract val itemsYml: RegularFileProperty

  @get:InputDirectory
  abstract val clientDbDir: DirectoryProperty

  @get:InputFile
  abstract val itemsCsv: RegularFileProperty

  /** If true, patch/create files. If false, only report drift and fail the build on any. */
  @get:Input
  abstract val fix: Property<Boolean>

  private data class ItemDto(
    val id: Long,
    @JsonProperty("item-db-name")
    val identifier: String,
    val weight: Int,
    val type: String,
    @JsonProperty("equip-slot")
    val equipSlot: String? = null,
    val description: String? = null
  )

  private data class ItemsFile(val items: List<ItemDto> = emptyList())

  private data class Expected(
    val identifier: String,
    val weight: Int,
    val type: Int,
    val equipSlot: Int,
    val description: String?
  )

  @TaskAction
  fun run() {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val items = mapper.readValue(itemsYml.get().asFile, ItemsFile::class.java).items

    val expectedById = items.associate { item ->
      item.id to Expected(
        identifier = item.identifier,
        weight = item.weight,
        type = typeOrdinalOf(item),
        equipSlot = equipSlotValueOf(item),
        description = item.description?.trim()?.takeIf { it.isNotEmpty() }
      )
    }

    val dbDir = clientDbDir.get().asFile
    val tresFiles = dbDir.listFiles { f -> f.isFile && f.extension == "tres" }?.toList() ?: emptyList()

    // Anchored to line start: a bare `type = ...` would otherwise also match the `type="Resource"`
    // in the gd_resource/ext_resource header lines.
    val itemIdPattern = Regex("""^item_id\s*=\s*(\d+)""", RegexOption.MULTILINE)
    val weightPattern = Regex("""^weight\s*=\s*(\d+)""", RegexOption.MULTILINE)
    val typePattern = Regex("""^type\s*=\s*(\d+)""", RegexOption.MULTILINE)
    val equipSlotPattern = Regex("""^equip_slot\s*=\s*(\d+)""", RegexOption.MULTILINE)
    val nameKeyPattern = Regex("""^name_key\s*=\s*"([^"]*)"""", RegexOption.MULTILINE)
    val descriptionKeyPattern = Regex("""^description_key\s*=\s*"([^"]*)"""", RegexOption.MULTILINE)

    val fileByItemId = mutableMapOf<Long, File>()
    for (file in tresFiles) {
      val id = itemIdPattern.find(file.readText())?.groupValues?.get(1)?.toLongOrNull()
      if (id != null) {
        fileByItemId[id] = file
      }
    }

    val problems = mutableListOf<String>()
    val shouldFix = fix.get()
    val csv = LocalizationCsv.load(itemsCsv.get().asFile)
    var csvDirty = false

    for ((id, expected) in expectedById) {
      val file = fileByItemId[id]
      val nameKey = expected.identifier.uppercase()
      val descriptionKey = "${expected.identifier.uppercase()}_DESC"

      if (file == null) {
        if (shouldFix) {
          val newFile = File(dbDir, "${id}_${expected.identifier.lowercase()}.tres")
          newFile.writeText(stubTres(id, expected, nameKey, descriptionKey))
          csvDirty = csv.upsertIfAbsent(nameKey, expected.identifier.replace('_', ' ')) || csvDirty
          csvDirty = if (expected.description != null) {
            csv.upsert(descriptionKey, expected.description) || csvDirty
          } else {
            csv.upsertIfAbsent(descriptionKey, "TODO: describe ${expected.identifier}") || csvDirty
          }
          logger.lifecycle("ItemDbSync: created stub ${newFile.name} - fill in icon/item_visual, translate $nameKey/$descriptionKey in items.csv")
        } else {
          problems += "item id=$id (${expected.identifier}) has no matching bestia-client/.../Item/DB/*.tres file"
        }
        continue
      }

      patchNumericField(file, "weight", weightPattern, expected.weight, id, shouldFix, problems)
      patchNumericField(file, "type", typePattern, expected.type, id, shouldFix, problems)

      // Only equipment needs the line at all; a usable/etc item is fine relying on the default 0.
      if (expected.equipSlot != 0) {
        patchNumericField(file, "equip_slot", equipSlotPattern, expected.equipSlot, id, shouldFix, problems)
      }

      // name_key/description_key are derived from the identifier, not hand-authored - keep them in
      // sync too, and carry over any existing translations if the key itself changes (e.g. after a
      // rename in items.yml, or migrating off the old ITEM_<id>_NAME/DESC scheme).
      val oldNameKey = patchStringField(file, "name_key", nameKeyPattern, nameKey, id, shouldFix, problems)
      if (shouldFix && oldNameKey != null && oldNameKey != nameKey) {
        csvDirty = csv.renameKey(oldNameKey, nameKey) || csvDirty
      }
      val oldDescriptionKey = patchStringField(file, "description_key", descriptionKeyPattern, descriptionKey, id, shouldFix, problems)
      if (shouldFix && oldDescriptionKey != null && oldDescriptionKey != descriptionKey) {
        csvDirty = csv.renameKey(oldDescriptionKey, descriptionKey) || csvDirty
      }

      // The English text is only server-sourced (and thus only checked/patched here) once items.yml
      // actually declares a description - until then it's hand-authored straight in items.csv.
      if (expected.description != null && csv.get(descriptionKey) != expected.description) {
        if (shouldFix) {
          csvDirty = csv.upsert(descriptionKey, expected.description) || csvDirty
          logger.lifecycle("ItemDbSync: synced $descriptionKey English text from items.yml into items.csv")
        } else {
          problems += "items.csv: '$descriptionKey' en text is missing/stale vs items.yml description (id=$id)"
        }
      }
    }

    val knownIds = expectedById.keys
    for ((id, file) in fileByItemId) {
      if (id !in knownIds) {
        problems += "${file.name}: item_id=$id has no corresponding entry in items.yml (orphaned client resource)"
      }
    }

    if (shouldFix && csvDirty) {
      itemsCsv.get().asFile.writeText(csv.render())
    }

    if (!shouldFix && problems.isNotEmpty()) {
      throw GradleException(
        "Item DB drift between items.yml and the client Item DB (.tres + items.csv):\n" +
          problems.joinToString("\n") { "  - $it" } +
          "\nRun './gradlew syncItemDb' to fix automatically."
      )
    }
  }

  private fun patchNumericField(
    file: File,
    name: String,
    pattern: Regex,
    expected: Int,
    id: Long,
    shouldFix: Boolean,
    problems: MutableList<String>
  ) {
    val text = file.readText()
    val current = pattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
    if (current == expected) return

    if (shouldFix) {
      val newText = if (pattern.containsMatchIn(text)) {
        pattern.replace(text) { "$name = $expected" }
      } else {
        text.trimEnd('\n') + "\n$name = $expected\n"
      }
      file.writeText(newText)
      logger.lifecycle("ItemDbSync: patched ${file.name}: $name ${current ?: "<missing>"} -> $expected")
    } else {
      problems += "${file.name}: $name=${current ?: "<missing>"} but items.yml expects $expected (id=$id)"
    }
  }

  /** Same idea as [patchNumericField], for a quoted string field. Returns the previous value
   *  (or null if the field was missing/already correct/not fixed), so callers can migrate
   *  anything keyed off the old value (e.g. renaming a CSV row). */
  private fun patchStringField(
    file: File,
    name: String,
    pattern: Regex,
    expected: String,
    id: Long,
    shouldFix: Boolean,
    problems: MutableList<String>
  ): String? {
    val text = file.readText()
    val current = pattern.find(text)?.groupValues?.get(1)
    if (current == expected) return null

    if (shouldFix) {
      val newText = if (pattern.containsMatchIn(text)) {
        pattern.replace(text) { "$name = \"$expected\"" }
      } else {
        text.trimEnd('\n') + "\n$name = \"$expected\"\n"
      }
      file.writeText(newText)
      logger.lifecycle("ItemDbSync: patched ${file.name}: $name ${current ?: "<missing>"} -> $expected")
      return current
    } else {
      problems += "${file.name}: $name=${current ?: "<missing>"} but items.yml expects $expected (id=$id)"
      return null
    }
  }

  private fun typeOrdinalOf(item: ItemDto): Int {
    val index = TYPE_ORDER.indexOf(item.type.uppercase())
    if (index < 0) {
      throw GradleException("Unknown item type '${item.type}' for item '${item.identifier}'. Known: $TYPE_ORDER")
    }
    return index
  }

  /** 0 means "not equipment"; everything else is the [SLOT_ORDER] index + 1. */
  private fun equipSlotValueOf(item: ItemDto): Int {
    val name = item.equipSlot ?: return 0
    val index = SLOT_ORDER.indexOf(name.uppercase())
    if (index < 0) {
      throw GradleException("Unknown equip slot '$name' for item '${item.identifier}'. Known: $SLOT_ORDER")
    }
    return index + 1
  }

  private fun stubTres(id: Long, expected: Expected, nameKey: String, descriptionKey: String): String {
    val equipSlotLine = if (expected.equipSlot != 0) "\nequip_slot = ${expected.equipSlot}" else ""
    return """
    [gd_resource type="Resource" script_class="ItemResource" load_steps=2 format=3]

    [ext_resource type="Script" path="res://Game/Item/item_resource.gd" id="1_script"]

    [resource]
    script = ExtResource("1_script")
    item_id = $id
    name_key = "$nameKey"
    description_key = "$descriptionKey"
    weight = ${expected.weight}
    type = ${expected.type}
    """.trimIndent() + equipSlotLine + "\n"
  }

  companion object {
    /** Must mirror `net.bestia.zone.item.Item.ItemType` and `ItemResource.ItemType`. */
    private val TYPE_ORDER = listOf("USABLE", "EQUIP", "ETC")

    /** Must mirror `net.bestia.zone.item.equip.EquipmentSlot`'s declaration order exactly. */
    private val SLOT_ORDER = listOf(
      "HEAD_UPPER",
      "HEAD_MID",
      "HEAD_LOWER",
      "ARMOR",
      "GARMENT",
      "FOOTGEAR",
      "RIGHT_HAND",
      "LEFT_HAND",
      "ACCESSORY_1",
      "ACCESSORY_2"
    )
  }
}
