package net.bestia.zone.util

/**
 * The same beans, ordered by class name. Spring hands over a list of beans in the order it found them, which
 * follows package paths, so moving a class between packages would silently reorder a list whose first match
 * wins.
 */
fun <T : Any> List<T>.inClassNameOrder(): List<T> {
  return sortedBy { it::class.java.simpleName }
}
