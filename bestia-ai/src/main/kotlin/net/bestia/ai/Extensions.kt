package net.bestia.ai

/**
 * Returns a list containing all elements that are instances of specified class.
 */
internal fun <R> Iterable<*>.firstIsInstanceOrNull(klass: Class<R>): R? {
  return filterIsInstanceTo(ArrayList(), klass).firstOrNull()
}
