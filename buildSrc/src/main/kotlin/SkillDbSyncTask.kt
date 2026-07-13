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
 * Cross-checks (and optionally fixes) skill ids/max levels/descriptions/target types between the
 * server's skill config (`skills.yml` + `master_skill_tree.yml`) and the Godot client's Attack DB
 * (`*.tres` files under `bestia-client/src/Game/Attack/DB/`). See
 * `.claude/skills/skill-system/SKILL.md` for the full relationship between these files.
 *
 * Only `skill_id`/`max_level`/`description_key`/`target_type`/`aoe_radius` are touched on a
 * `.tres` file — every other field (icon, name, mana_cost, cooldown) is hand-authored client
 * presentation with no server equivalent and is left alone. `aoe_radius` is only checked/patched
 * when `target_type` is `AOE_GROUND` - non-AOE skills never need the line at all.
 *
 * `description_key` points into `bestia-client/src/Localization/skills.csv`, a Godot CSV
 * translation source (same mechanism as `items.csv`). Whenever `skills.yml` declares a
 * `description` for a skill, this task also syncs that text into the CSV's `en` column - that is
 * the *only* column it ever writes. Any other language column, and any key whose skill has no
 * `description` in `skills.yml` yet, is hand-translated/hand-authored and left untouched.
 */
abstract class SkillDbSyncTask : DefaultTask() {

  @get:InputFile
  abstract val skillsYml: RegularFileProperty

  @get:InputFile
  abstract val masterSkillTreeYml: RegularFileProperty

  @get:InputDirectory
  abstract val clientDbDir: DirectoryProperty

  @get:InputFile
  abstract val skillsCsv: RegularFileProperty

  /** If true, patch/create files. If false, only report drift and fail the build on any. */
  @get:Input
  abstract val fix: Property<Boolean>

  private data class SkillDto(
    val id: Long,
    val identifier: String,
    val description: String? = null,
    val targetType: String,
    val aoeRadius: Double? = null
  )
  private data class SkillsFile(val skills: List<SkillDto> = emptyList())
  private data class TreeNodeDto(val skill: String, val maxLevel: Int)
  private data class TreeFile(val skills: List<TreeNodeDto> = emptyList())
  private data class Expected(
    val identifier: String,
    val maxLevel: Int,
    val description: String?,
    val targetType: String,
    val aoeRadius: Double?
  )

  @TaskAction
  fun run() {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val skills = mapper.readValue(skillsYml.get().asFile, SkillsFile::class.java).skills
    val tree = mapper.readValue(masterSkillTreeYml.get().asFile, TreeFile::class.java).skills
    val maxLevelByIdentifier = tree.associate { it.skill to it.maxLevel }

    val expectedById = skills.associate { skill ->
      // A skill absent from master_skill_tree.yml is not master-investable (e.g. a bestia-only
      // skill) and is always single-rank client-side.
      skill.id to Expected(
        skill.identifier,
        maxLevelByIdentifier[skill.identifier] ?: 1,
        skill.description?.trim()?.takeIf { it.isNotEmpty() },
        skill.targetType,
        skill.aoeRadius
      )
    }

    val dbDir = clientDbDir.get().asFile
    val tresFiles = dbDir.listFiles { f -> f.isFile && f.extension == "tres" }?.toList() ?: emptyList()

    val skillIdPattern = Regex("""skill_id\s*=\s*(\d+)""")
    val maxLevelPattern = Regex("""max_level\s*=\s*(\d+)""")
    val descriptionKeyPattern = Regex("description_key\\s*=\\s*\"([^\"]*)\"")
    val targetTypePattern = Regex("target_type\\s*=\\s*\"([^\"]*)\"")
    val aoeRadiusPattern = Regex("""aoe_radius\s*=\s*([\d.]+)""")

    val fileBySkillId = mutableMapOf<Long, File>()
    for (file in tresFiles) {
      val id = skillIdPattern.find(file.readText())?.groupValues?.get(1)?.toLongOrNull()
      if (id != null) {
        fileBySkillId[id] = file
      }
    }

    val problems = mutableListOf<String>()
    val shouldFix = fix.get()
    val csv = LocalizationCsv.load(skillsCsv.get().asFile)
    var csvDirty = false

    for ((id, expected) in expectedById) {
      val file = fileBySkillId[id]
      val descriptionKey = "SKILL_${id}_DESC"

      if (file == null) {
        if (shouldFix) {
          val newFile = File(dbDir, "${id}_${expected.identifier.lowercase()}.tres")
          newFile.writeText(stubTres(id, expected, descriptionKey))
          if (expected.description != null) {
            csvDirty = csv.upsert(descriptionKey, expected.description) || csvDirty
          } else {
            csvDirty = csv.upsertIfAbsent(descriptionKey, "TODO: describe ${expected.identifier}") || csvDirty
          }
          logger.lifecycle("SkillDbSync: created stub ${newFile.name} - fill in icon/name/mana_cost/cooldown, translate $descriptionKey in skills.csv")
        } else {
          problems += "skill id=$id (${expected.identifier}) has no matching bestia-client/.../DB/*.tres file"
        }
        continue
      }

      val text = file.readText()
      val currentMaxLevel = maxLevelPattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
      val currentDescriptionKey = descriptionKeyPattern.find(text)?.groupValues?.get(1)

      if (currentMaxLevel != expected.maxLevel) {
        if (shouldFix) {
          file.writeText(maxLevelPattern.replace(text) { "max_level = ${expected.maxLevel}" })
          logger.lifecycle("SkillDbSync: patched ${file.name}: max_level $currentMaxLevel -> ${expected.maxLevel}")
        } else {
          problems += "${file.name}: max_level=$currentMaxLevel but skills.yml/master_skill_tree.yml expect ${expected.maxLevel} (id=$id)"
        }
      }

      if (currentDescriptionKey != descriptionKey) {
        if (shouldFix) {
          val patched = file.readText()
          val newText = if (descriptionKeyPattern.containsMatchIn(patched)) {
            descriptionKeyPattern.replace(patched) { "description_key = \"$descriptionKey\"" }
          } else {
            patched.trimEnd('\n') + "\ndescription_key = \"$descriptionKey\"\n"
          }
          file.writeText(newText)
          logger.lifecycle("SkillDbSync: patched ${file.name}: description_key ${currentDescriptionKey ?: "<missing>"} -> $descriptionKey")
        } else {
          problems += "${file.name}: description_key=${currentDescriptionKey ?: "<missing>"} but expected $descriptionKey (id=$id)"
        }
      }

      val currentTargetType = targetTypePattern.find(text)?.groupValues?.get(1)

      if (currentTargetType != expected.targetType) {
        if (shouldFix) {
          val patched = file.readText()
          val newText = if (targetTypePattern.containsMatchIn(patched)) {
            targetTypePattern.replace(patched) { "target_type = \"${expected.targetType}\"" }
          } else {
            patched.trimEnd('\n') + "\ntarget_type = \"${expected.targetType}\"\n"
          }
          file.writeText(newText)
          logger.lifecycle("SkillDbSync: patched ${file.name}: target_type ${currentTargetType ?: "<missing>"} -> ${expected.targetType}")
        } else {
          problems += "${file.name}: target_type=${currentTargetType ?: "<missing>"} but expected ${expected.targetType} (id=$id)"
        }
      }

      // aoe_radius is only meaningful (and thus only checked/patched) for AOE_GROUND skills - every
      // other target type never needs the line at all.
      if (expected.targetType == "AOE_GROUND") {
        val currentText = file.readText()
        val currentAoeRadius = aoeRadiusPattern.find(currentText)?.groupValues?.get(1)?.toDoubleOrNull()

        if (currentAoeRadius != expected.aoeRadius) {
          if (shouldFix) {
            val newText = if (aoeRadiusPattern.containsMatchIn(currentText)) {
              aoeRadiusPattern.replace(currentText) { "aoe_radius = ${expected.aoeRadius}" }
            } else {
              currentText.trimEnd('\n') + "\naoe_radius = ${expected.aoeRadius}\n"
            }
            file.writeText(newText)
            logger.lifecycle("SkillDbSync: patched ${file.name}: aoe_radius $currentAoeRadius -> ${expected.aoeRadius}")
          } else {
            problems += "${file.name}: aoe_radius=$currentAoeRadius but skills.yml expects ${expected.aoeRadius} (id=$id)"
          }
        }
      }

      // The English text itself is only server-sourced (and thus only checked/patched here) once
      // skills.yml actually declares a description for this skill - until then it's hand-authored
      // straight in skills.csv, same as every other still-unsynced client field.
      if (expected.description != null) {
        val currentEn = csv.get(descriptionKey)
        if (currentEn != expected.description) {
          if (shouldFix) {
            csvDirty = csv.upsert(descriptionKey, expected.description) || csvDirty
            logger.lifecycle("SkillDbSync: synced $descriptionKey English text from skills.yml into skills.csv")
          } else {
            problems += "skills.csv: '$descriptionKey' en text is missing/stale vs skills.yml description (id=$id)"
          }
        }
      }
    }

    val knownIds = expectedById.keys
    for ((id, file) in fileBySkillId) {
      if (id !in knownIds) {
        problems += "${file.name}: skill_id=$id has no corresponding entry in skills.yml (orphaned client resource)"
      }
    }

    if (shouldFix && csvDirty) {
      skillsCsv.get().asFile.writeText(csv.render())
    }

    if (!shouldFix && problems.isNotEmpty()) {
      throw GradleException(
        "Skill DB drift between skills.yml/master_skill_tree.yml and the client Attack DB:\n" +
          problems.joinToString("\n") { "  - $it" } +
          "\nRun './gradlew syncSkillDb' to fix automatically."
      )
    }
  }

  private fun stubTres(id: Long, expected: Expected, descriptionKey: String): String {
    val aoeRadiusLine = if (expected.aoeRadius != null) "\naoe_radius = ${expected.aoeRadius}" else ""
    return """
    [gd_resource type="Resource" script_class="AttackResource" load_steps=2 format=3]

    [ext_resource type="Script" path="res://Game/Attack/attack_resource.gd" id="1_script"]

    [resource]
    script = ExtResource("1_script")
    skill_id = $id
    name = "${expected.identifier}"
    description_key = "$descriptionKey"
    mana_cost = 0
    cooldown = 0.0
    max_level = ${expected.maxLevel}
    target_type = "${expected.targetType}"
    """.trimIndent() + aoeRadiusLine + "\n"
  }
}

/**
 * A minimal in-memory model of a Godot CSV translation source (e.g. `items.csv`, `skills.csv`):
 * header `keys,en[,de,fr,...]`, one row per translation key. Preserves any language column this
 * task doesn't know about (and every row it doesn't touch) verbatim, since those are filled in by
 * hand/by a translator, not generated.
 */
private class LocalizationCsv(
  private val header: MutableList<String>,
  private val rows: MutableList<LinkedHashMap<String, String>>
) {

  fun get(key: String): String? = rows.firstOrNull { it["keys"] == key }?.get("en")

  /** Inserts or updates the `en` column for [key]. Returns true if the CSV content changed. */
  fun upsert(key: String, enValue: String): Boolean {
    val row = rows.firstOrNull { it["keys"] == key }
    if (row != null) {
      if (row["en"] == enValue) return false
      row["en"] = enValue
      return true
    }
    val newRow = LinkedHashMap<String, String>()
    header.forEach { column -> newRow[column] = "" }
    newRow["keys"] = key
    newRow["en"] = enValue
    rows += newRow
    return true
  }

  fun upsertIfAbsent(key: String, enValue: String): Boolean {
    if (rows.any { it["keys"] == key }) return false
    return upsert(key, enValue)
  }

  fun render(): String {
    val sb = StringBuilder()
    sb.append(header.joinToString(",") { csvField(it) }).append("\n")
    for (row in rows) {
      sb.append(header.joinToString(",") { csvField(row[it] ?: "") }).append("\n")
    }
    return sb.toString()
  }

  private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
      "\"" + value.replace("\"", "\"\"") + "\""
    } else {
      value
    }

  companion object {
    private val DEFAULT_HEADER = mutableListOf("keys", "en")

    fun load(file: File): LocalizationCsv {
      if (!file.exists()) {
        return LocalizationCsv(DEFAULT_HEADER.toMutableList(), mutableListOf())
      }
      val records = parseCsv(file.readText())
      if (records.isEmpty()) {
        return LocalizationCsv(DEFAULT_HEADER.toMutableList(), mutableListOf())
      }
      val header = records.first().toMutableList()
      val rows = records.drop(1).map { record ->
        val row = LinkedHashMap<String, String>()
        header.forEachIndexed { i, column -> row[column] = record.getOrElse(i) { "" } }
        row
      }.toMutableList()
      return LocalizationCsv(header, rows)
    }

    /**
     * A minimal RFC4180 CSV parser: handles quoted fields with embedded commas, newlines, and
     * `""`-escaped quotes - which is all `skills.yml`'s BBCode descriptions need.
     */
    private fun parseCsv(text: String): List<List<String>> {
      val records = mutableListOf<MutableList<String>>()
      var field = StringBuilder()
      var record = mutableListOf<String>()
      var inQuotes = false
      var i = 0
      while (i < text.length) {
        val c = text[i]
        if (inQuotes) {
          when {
            c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
              field.append('"')
              i++
            }
            c == '"' -> inQuotes = false
            else -> field.append(c)
          }
        } else {
          when (c) {
            '"' -> inQuotes = true
            ',' -> {
              record.add(field.toString())
              field = StringBuilder()
            }
            '\r' -> {}
            '\n' -> {
              record.add(field.toString())
              field = StringBuilder()
              records.add(record)
              record = mutableListOf()
            }
            else -> field.append(c)
          }
        }
        i++
      }
      if (field.isNotEmpty() || record.isNotEmpty()) {
        record.add(field.toString())
        records.add(record)
      }
      return records.filterNot { it.size == 1 && it[0].isEmpty() }
    }
  }
}
