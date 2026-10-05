package net.bestia.login.account

/**
 * The rules of a display name. Recovery looks an account up by it and a passkey picker shows it, so two names that
 * look the same must be the same name.
 */
object DisplayNames {

  const val MIN_LENGTH = 3
  const val MAX_LENGTH = 32

  private val SPACES = Regex(" +")

  /**
   * The name as it is stored, or null when it breaks a rule. ASCII only: a Cyrillic "а" looks like a Latin "a",
   * so a name with one could pass for someone else's.
   */
  fun normalizeOrNull(raw: String): String? {
    val name = raw.trim().replace(SPACES, " ")

    if (name.length !in MIN_LENGTH..MAX_LENGTH || !name.all(::isAllowed)) {
      return null
    }

    return name
  }

  private fun isAllowed(c: Char): Boolean {
    return c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == ' ' || c == '_' || c == '-'
  }
}
