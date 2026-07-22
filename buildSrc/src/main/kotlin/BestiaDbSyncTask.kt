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
 * Exports each mob's **equipment slot mask** from the server's mob config
 * (the YML files under `zone-server/src/main/resources/mob/`) into the Godot client's static bestia
 * DB (the `.tres` files under `bestia-client/src/Game/Bestia/DB/`), the same check/sync split as
 * [SkillDbSyncTask].
 *
 * Which slots a bestia species even has is static content, not per-player state, so it is never
 * streamed over the wire - the client reads it from these resources and greys out the slots a
 * species does not have, while the server independently enforces the same mask from the `bestia`
 * table.
 *
 * The mask bit order is `EquipmentSlot`'s declaration order in
 * `zone-server/src/main/kotlin/net/bestia/zone/item/equip/EquipmentSlot.kt`; [SLOT_ORDER] below
 * must stay identical to it, and both must stay identical to `Game/Item/equipment_slot.gd` on the
 * client.
 *
 * Only `bestia_id` and `equip_slots` are touched. Every other field on a `BestiaResource` is
 * hand-authored client presentation with no server equivalent and is left alone.
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
    val equipSlots: List<String> = emptyList()
  )

  @TaskAction
  fun run() {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val ymlFiles = mobYmlDir.get().asFile.listFiles { f -> f.isFile && f.extension == "yml" }?.toList() ?: emptyList()
    val mobs = ymlFiles.map { mapper.readValue(it, MobDto::class.java) }

    val expectedMaskById = mobs.associate { mob -> mob.id to (mob.identifier to maskOf(mob)) }

    val dbDir = clientDbDir.get().asFile
    if (!dbDir.exists()) {
      if (!fix.get()) {
        throw GradleException("Client bestia DB directory ${dbDir.path} does not exist. Run './gradlew syncBestiaDb'.")
      }
      dbDir.mkdirs()
    }

    val tresFiles = dbDir.listFiles { f -> f.isFile && f.extension == "tres" }?.toList() ?: emptyList()

    val bestiaIdPattern = Regex("""^bestia_id\s*=\s*(\d+)""", RegexOption.MULTILINE)
    val equipSlotsPattern = Regex("""^equip_slots\s*=\s*(\d+)""", RegexOption.MULTILINE)

    val fileByBestiaId = mutableMapOf<Long, File>()
    for (file in tresFiles) {
      val id = bestiaIdPattern.find(file.readText())?.groupValues?.get(1)?.toLongOrNull()
      if (id != null) {
        fileByBestiaId[id] = file
      }
    }

    val problems = mutableListOf<String>()
    val shouldFix = fix.get()

    for ((id, expected) in expectedMaskById) {
      val (identifier, mask) = expected
      val file = fileByBestiaId[id]

      if (file == null) {
        if (shouldFix) {
          val newFile = File(dbDir, "${id}_${identifier.lowercase()}.tres")
          newFile.writeText(stubTres(id, mask))
          logger.lifecycle("BestiaDbSync: created ${newFile.name} (equip_slots = $mask)")
        } else {
          problems += "bestia id=$id ($identifier) has no matching bestia-client/.../Bestia/DB/*.tres file"
        }
        continue
      }

      val text = file.readText()
      val currentMask = equipSlotsPattern.find(text)?.groupValues?.get(1)?.toIntOrNull()

      if (currentMask != mask) {
        if (shouldFix) {
          val newText = if (equipSlotsPattern.containsMatchIn(text)) {
            equipSlotsPattern.replace(text) { "equip_slots = $mask" }
          } else {
            text.trimEnd('\n') + "\nequip_slots = $mask\n"
          }
          file.writeText(newText)
          logger.lifecycle("BestiaDbSync: patched ${file.name}: equip_slots ${currentMask ?: "<missing>"} -> $mask")
        } else {
          problems += "${file.name}: equip_slots=${currentMask ?: "<missing>"} but the mob YML expects $mask (id=$id)"
        }
      }
    }

    val knownIds = expectedMaskById.keys
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

  private fun stubTres(id: Long, mask: Int): String {
    return """
    [gd_resource type="Resource" script_class="BestiaResource" load_steps=2 format=3]

    [ext_resource type="Script" path="res://Game/Bestia/bestia_resource.gd" id="1_script"]

    [resource]
    script = ExtResource("1_script")
    bestia_id = $id
    equip_slots = $mask
    """.trimIndent() + "\n"
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
