import java.io.File

/**
 * A minimal in-memory model of a Godot CSV translation source (e.g. `items.csv`, `skills.csv`):
 * header `keys,en[,de,fr,...]`, one row per translation key. Preserves any language column the
 * calling task doesn't know about (and every row it doesn't touch) verbatim, since those are filled
 * in by hand/by a translator, not generated.
 *
 * Shared by [SkillDbSyncTask] and [ItemDbSyncTask]; both write only the `en` column.
 */
internal class LocalizationCsv(
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
