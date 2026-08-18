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
 * Only `skill_id`/`max_level`/`description_key`/`target_type`/`aoe_radius`/`cast_time`/`tree`/
 * `sub_tree`/`is_passive` are touched on a `.tres` file — every other field (icon, name, mana_cost, cooldown) is hand-authored client
 * presentation with no server equivalent and is left alone. `aoe_radius` is only checked/patched
 * when `target_type` is `AOE_GROUND` - non-AOE skills never need the line at all. `cast_time` is
 * likewise only checked/patched for skills that declare a non-zero `castTime` server-side; an
 * instant-cast skill never needs the line.
 *
 * A `.tres` saved from the Godot inspector - which is how `icon` gets authored - is rewritten whole,
 * and every property still equal to its script default is dropped. A missing line therefore reads as
 * the resource default rather than as drift, or assigning an icon in the editor would fail the build.
 *
 * `description_key` points into `bestia-client/src/Localization/skills.csv`, a Godot CSV
 * translation source (same mechanism as `items.csv`). Whenever `skills.yml` declares a
 * `description` for a skill, this task also syncs that text into the CSV's `en` column - that is
 * the *only* column it ever writes. Any other language column, and any key whose skill has no
 * `description` in `skills.yml` yet, is hand-translated/hand-authored and left untouched.
 */
abstract class SkillDbSyncTask : DefaultTask() {

  private companion object {
    /** Mirrors the `@export` defaults in `attack_resource.gd`. */
    const val DEFAULT_MAX_LEVEL = 1
    const val DEFAULT_TARGET_TYPE = "GROUND"
    const val DEFAULT_IS_PASSIVE = false

    /** The `net.bestia.zone.battle.skill.SkillType` name the client cares about, as spelled in `skills.yml`. */
    const val PASSIVE_SKILL_TYPE = "PASSIVE"
  }

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
    val type: String? = null,
    val targetType: String,
    val aoeRadius: Double? = null,
    val castTime: Double = 0.0
  )
  private data class SkillsFile(val skills: List<SkillDto> = emptyList())
  private data class TreeNodeDto(
    val skill: String,
    val maxLevel: Int,
    val tree: String? = null,
    val subTree: String? = null
  )
  private data class TreeFile(val skills: List<TreeNodeDto> = emptyList())
  private data class Expected(
    val identifier: String,
    val maxLevel: Int,
    val description: String?,
    val targetType: String,
    val aoeRadius: Double?,
    val castTime: Double,

    /**
     * The tree/sub-tree the Skills window groups this skill under. Empty for a skill outside the
     * master tree entirely (a bestia or item-taught one), which the client shows in its own tab.
     */
    val tree: String,
    val subTree: String,

    /**
     * Whether the server catalogues this as [net.bestia.zone.battle.skill.SkillType.PASSIVE], the one
     * distinction the client draws between skill types - a passive is never cast, so the client hides
     * every way to activate it. The other types only matter to the server's damage formula.
     */
    val isPassive: Boolean
  )

  @TaskAction
  fun run() {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val skills = mapper.readValue(skillsYml.get().asFile, SkillsFile::class.java).skills
    val tree = mapper.readValue(masterSkillTreeYml.get().asFile, TreeFile::class.java).skills
    val nodeByIdentifier = tree.associateBy { it.skill }

    val expectedById = skills.associate { skill ->
      // A skill absent from master_skill_tree.yml is not master-investable (e.g. a bestia-only
      // skill) and is always single-rank client-side.
      val node = nodeByIdentifier[skill.identifier]

      skill.id to Expected(
        skill.identifier,
        node?.maxLevel ?: 1,
        skill.description?.trim()?.takeIf { it.isNotEmpty() },
        skill.targetType,
        skill.aoeRadius,
        skill.castTime,
        node?.tree.orEmpty(),
        node?.subTree.orEmpty(),
        skill.type == PASSIVE_SKILL_TYPE
      )
    }

    val dbDir = clientDbDir.get().asFile
    val tresFiles = dbDir.listFiles { f -> f.isFile && f.extension == "tres" }?.toList() ?: emptyList()

    val skillIdPattern = Regex("""skill_id\s*=\s*(\d+)""")
    val maxLevelPattern = Regex("""max_level\s*=\s*(\d+)""")
    val descriptionKeyPattern = Regex("description_key\\s*=\\s*\"([^\"]*)\"")
    val targetTypePattern = Regex("target_type\\s*=\\s*\"([^\"]*)\"")
    val aoeRadiusPattern = Regex("""aoe_radius\s*=\s*([\d.]+)""")
    val castTimePattern = Regex("""cast_time\s*=\s*([\d.]+)""")
    // Anchored: unanchored, `tree = "..."` also matches inside a `sub_tree` line, and the patch below
    // would then rewrite sub_tree's value.
    val treePattern = Regex("^tree\\s*=\\s*\"([^\"]*)\"", RegexOption.MULTILINE)
    val subTreePattern = Regex("^sub_tree\\s*=\\s*\"([^\"]*)\"", RegexOption.MULTILINE)
    val isPassivePattern = Regex("""is_passive\s*=\s*(true|false)""")
    val iconPattern = Regex("^icon\\s*=", RegexOption.MULTILINE)

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
        ?: DEFAULT_MAX_LEVEL
      val currentDescriptionKey = descriptionKeyPattern.find(text)?.groupValues?.get(1)

      if (currentMaxLevel != expected.maxLevel) {
        if (shouldFix) {
          // Append rather than replace when the line is absent - Regex.replace would no-op and the
          // drift would survive the fix.
          val newMaxLevelText = if (maxLevelPattern.containsMatchIn(text)) {
            maxLevelPattern.replace(text) { "max_level = ${expected.maxLevel}" }
          } else {
            text.trimEnd('\n') + "\nmax_level = ${expected.maxLevel}\n"
          }
          file.writeText(newMaxLevelText)
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

      val currentTargetType = targetTypePattern.find(text)?.groupValues?.get(1) ?: DEFAULT_TARGET_TYPE

      if (currentTargetType != expected.targetType) {
        if (shouldFix) {
          val patched = file.readText()
          val newText = if (targetTypePattern.containsMatchIn(patched)) {
            targetTypePattern.replace(patched) { "target_type = \"${expected.targetType}\"" }
          } else {
            patched.trimEnd('\n') + "\ntarget_type = \"${expected.targetType}\"\n"
          }
          file.writeText(newText)
          logger.lifecycle("SkillDbSync: patched ${file.name}: target_type $currentTargetType -> ${expected.targetType}")
        } else {
          problems += "${file.name}: target_type=$currentTargetType but expected ${expected.targetType} (id=$id)"
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

      // cast_time only needs to exist client-side for skills that actually channel; an instant skill
      // is fine relying on the resource default of 0.0.
      if (expected.castTime > 0.0) {
        val currentText = file.readText()
        val currentCastTime = castTimePattern.find(currentText)?.groupValues?.get(1)?.toDoubleOrNull()

        if (currentCastTime != expected.castTime) {
          if (shouldFix) {
            val newText = if (castTimePattern.containsMatchIn(currentText)) {
              castTimePattern.replace(currentText) { "cast_time = ${expected.castTime}" }
            } else {
              currentText.trimEnd('\n') + "\ncast_time = ${expected.castTime}\n"
            }
            file.writeText(newText)
            logger.lifecycle("SkillDbSync: patched ${file.name}: cast_time $currentCastTime -> ${expected.castTime}")
          } else {
            problems += "${file.name}: cast_time=$currentCastTime but skills.yml expects ${expected.castTime} (id=$id)"
          }
        }
      }

      for ((field, pattern, expectedValue) in listOf(
        Triple("tree", treePattern, expected.tree),
        Triple("sub_tree", subTreePattern, expected.subTree)
      )) {
        val currentText = file.readText()
        val current = pattern.find(currentText)?.groupValues?.get(1).orEmpty()

        if (current != expectedValue) {
          if (shouldFix) {
            val newText = if (pattern.containsMatchIn(currentText)) {
              pattern.replace(currentText) { "$field = \"$expectedValue\"" }
            } else {
              currentText.trimEnd('\n') + "\n$field = \"$expectedValue\"\n"
            }
            file.writeText(newText)
            logger.lifecycle("SkillDbSync: patched ${file.name}: $field '$current' -> '$expectedValue'")
          } else {
            problems += "${file.name}: $field='$current' but master_skill_tree.yml expects '$expectedValue' (id=$id)"
          }
        }
      }

      // Only the client's one type distinction is mirrored: whether the skill is PASSIVE, which
      // decides whether the Skills window offers any way to cast it. MAGIC vs MELEE_PHYSICAL vs the
      // rest is server-side damage math the client never needs.
      run {
        val currentText = file.readText()
        val currentIsPassive = isPassivePattern.find(currentText)?.groupValues?.get(1)?.toBoolean()
          ?: DEFAULT_IS_PASSIVE

        if (currentIsPassive != expected.isPassive) {
          if (shouldFix) {
            val newText = if (isPassivePattern.containsMatchIn(currentText)) {
              isPassivePattern.replace(currentText) { "is_passive = ${expected.isPassive}" }
            } else {
              currentText.trimEnd('\n') + "\nis_passive = ${expected.isPassive}\n"
            }
            file.writeText(newText)
            logger.lifecycle("SkillDbSync: patched ${file.name}: is_passive $currentIsPassive -> ${expected.isPassive}")
          } else {
            problems += "${file.name}: is_passive=$currentIsPassive but skills.yml expects ${expected.isPassive} (id=$id)"
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

    // icon is hand-authored art with no server equivalent, so an unfinished one is a to-do rather than
    // drift - AttackResource.get_icon() already falls back to a visible placeholder.
    val missingIcons = fileBySkillId.filterKeys { it in knownIds }
      .filterValues { !iconPattern.containsMatchIn(it.readText()) }
      .keys.sorted()
    if (missingIcons.isNotEmpty()) {
      logger.warn(
        "SkillDbSync: ${missingIcons.size} of ${knownIds.size} skills have no icon yet " +
          "(ids: ${missingIcons.joinToString(", ")})"
      )
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
    // Omitted when they equal the resource default, both to match what a Godot save leaves behind and
    // because the checks above read a missing line as that default.
    val maxLevelLine = if (expected.maxLevel != DEFAULT_MAX_LEVEL) "\nmax_level = ${expected.maxLevel}" else ""
    val targetTypeLine =
      if (expected.targetType != DEFAULT_TARGET_TYPE) "\ntarget_type = \"${expected.targetType}\"" else ""
    val aoeRadiusLine = if (expected.aoeRadius != null) "\naoe_radius = ${expected.aoeRadius}" else ""
    val castTimeLine = if (expected.castTime > 0.0) "\ncast_time = ${expected.castTime}" else ""
    // Omitted for a skill outside the master tree: the resource default is the empty string the
    // check compares against, so a bestia skill wants no line rather than an empty one.
    val treeLine = if (expected.tree.isNotEmpty()) "\ntree = \"${expected.tree}\"" else ""
    val subTreeLine = if (expected.subTree.isNotEmpty()) "\nsub_tree = \"${expected.subTree}\"" else ""
    val isPassiveLine = if (expected.isPassive != DEFAULT_IS_PASSIVE) "\nis_passive = ${expected.isPassive}" else ""
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
    """.trimIndent() + maxLevelLine + targetTypeLine + aoeRadiusLine + castTimeLine +
      treeLine + subTreeLine + isPassiveLine + "\n"
  }
}
