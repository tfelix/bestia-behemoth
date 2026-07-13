package net.bestia.zone

// Deliberately a RuntimeException, not the checked java.lang.Exception: Kotlin has no checked
// exceptions at the language level, but the JVM still does, and Spring's @Transactional only
// rolls back automatically on RuntimeException/Error by default. A checked BestiaException
// thrown from inside a @Transactional method would let Spring commit whatever had already been
// saved instead of rolling back the whole unit of work.
open class BestiaException(
  code: String,
  message: String,
  cause: Throwable? = null
) : RuntimeException(message, cause)