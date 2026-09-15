import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Exports the **per-species facts the client decides things with** from the server's mob config (the
 * YML files under `zone-server/src/main/resources/mob/`) into the Godot client's static bestia DB (the
 * `.tres` files under `bestia-client/src/Game/Bestia/DB/`), the same check/sync split as
 * [SkillDbSyncTask].
 *
 * These are static content, not per-player state, so they are never streamed over the wire - the client
 * reads them from these resources while the server independently enforces the same values from the
 * `bestia` table. Today that is which equipment slots a species has, so the UI can grey out the rest,
 * and whether it is a non-combatant, so a click can mean talking rather than swinging.
 *
 * The mask bit order is `EquipmentSlot`'s declaration order in
 * `zone-server/src/main/kotlin/net/bestia/zone/item/equip/EquipmentSlot.kt`; [SLOT_ORDER] below
 * must stay identical to it, and both must stay identical to `Game/Item/equipment_slot.gd` on the
 * client.
 *
 * Only `bestia_id` and the fields [exportedFields] names are touched. Every other field on a
 * `BestiaResource` is hand-authored client presentation with no server equivalent and is left alone.
 */
abstract class BestiaDbSyncTask : DefaultTask() {

  @get:InputDirectory
  abstract val mobYmlDir: DirectoryProperty

  @get:InputDirectory
  abstract val clientDbDir: DirectoryProperty

  /** If true, patch/create files. If false, only report drift and fail the build on any. */
  @get:Input
  abstract val fix: Property<Boolean>

  private data class MobDto(
    val id: Long,
    val identifier: String,
    @JsonProperty("equip-slots")
    val equipSlots: List<String> = emptyList(),
    @JsonProperty("non-combatant")
    val nonCombatant: Boolean = false
  )

  /**
   * What this task owns in a `.tres`, keyed by the name Godot spells it with.
   *
   * Written even when the value matches `BestiaResource`'s own default, so "absent" is always drift
   * rather than sometimes meaning agreement.
   */
  private fun exportedFields(mob: MobDto): Map<String, String> = mapOf(
    "equip_slots" to maskOf(mob).toString(),
    "non_combatant" to mob.nonCombatant.toString()
  )

  @TaskAction
  fun run() {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val ymlFiles = mobYmlDir.get().asFile.listFiles { f -> f.isFile && f.extension == "yml" }?.toList() ?: emptyList()
    val mobs = ymlFiles.map { mapper.readValue(it, MobDto::class.java) }

    val mobById = mobs.associateBy { it.id }

    val dbDir = clientDbDir.get().asFile
    if (!dbDir.exists()) {
      if (!fix.get()) {
        throw GradleException("Client bestia DB directory ${dbDir.path} does not exist. Run './gradlew syncBestiaDb'.")
      }
      dbDir.mkdirs()
    }

    val tresFiles = dbDir.listFiles { f -> f.isFile && f.extension == "tres" }?.toList() ?: emptyList()

    val bestiaIdPattern = Regex("""^bestia_id\s*=\s*(\d+)""", RegexOption.MULTILINE)

    val fileByBestiaId = mutableMapOf<Long, File>()
    for (file in tresFiles) {
      val id = bestiaIdPattern.find(file.readText())?.groupValues?.get(1)?.toLongOrNull()
      if (id != null) {
        fileByBestiaId[id] = file
      }
    }

    val problems = mutableListOf<String>()
    val shouldFix = fix.get()

    for ((id, mob) in mobById) {
      val expected = exportedFields(mob)
      val file = fileByBestiaId[id]

      if (file == null) {
        if (shouldFix) {
          val newFile = File(dbDir, "${id}_${mob.identifier.lowercase()}.tres")
          newFile.writeText(stubTres(id, expected))
          logger.lifecycle("BestiaDbSync: created ${newFile.name}")
        } else {
          problems += "bestia id=$id (${mob.identifier}) has no matching bestia-client/.../Bestia/DB/*.tres file"
        }
        continue
      }

      var text = file.readText()
      var patched = false

      for ((field, want) in expected) {
        val pattern = fieldPattern(field)
        val current = pattern.find(text)?.groupValues?.get(1)

        if (current == want) {
          continue
        }

        if (shouldFix) {
          text = if (pattern.containsMatchIn(text)) {
            pattern.replace(text) { "$field = $want" }
          } else {
            text.trimEnd('\n') + "\n$field = $want\n"
          }
          patched = true
          logger.lifecycle("BestiaDbSync: patched ${file.name}: $field ${current ?: "<missing>"} -> $want")
        } else {
          problems += "${file.name}: $field=${current ?: "<missing>"} but the mob YML expects $want (id=$id)"
        }
      }

      if (patched) {
        file.writeText(text)
      }
    }

    val knownIds = mobById.keys
    for ((id, file) in fileByBestiaId) {
      if (id !in knownIds) {
        problems += "${file.name}: bestia_id=$id has no corresponding mob YML (orphaned client resource)"
      }
    }

    if (!shouldFix && problems.isNotEmpty()) {
      throw GradleException(
        "Bestia DB drift between the mob YMLs and the client bestia DB:\n" +
          problems.joinToString("\n") { "  - $it" } +
          "\nRun './gradlew syncBestiaDb' to fix automatically."
      )
    }
  }

  private fun maskOf(mob: MobDto): Int {
    return mob.equipSlots.fold(0) { mask, name ->
      val index = SLOT_ORDER.indexOf(name.uppercase())
      if (index < 0) {
        throw GradleException("Unknown equip slot '$name' for mob '${mob.identifier}'. Known: $SLOT_ORDER")
      }
      mask or (1 shl index)
    }
  }

  private fun fieldPattern(field: String) = Regex("""^$field\s*=\s*(\S+)""", RegexOption.MULTILINE)

  private fun stubTres(id: Long, fields: Map<String, String>): String {
    val header = """
    [gd_resource type="Resource" script_class="BestiaResource" load_steps=2 format=3]

    [ext_resource type="Script" path="res://Game/Bestia/bestia_resource.gd" id="1_script"]

    [resource]
    script = ExtResource("1_script")
    bestia_id = $id
    """.trimIndent()

    return header + "\n" + fields.entries.joinToString("\n") { "${it.key} = ${it.value}" } + "\n"
  }

  companion object {
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
