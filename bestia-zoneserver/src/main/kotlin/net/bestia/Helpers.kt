package net.bestia

import java.util.*

fun <T> Optional<T>.getOrNull() : T? {
  return this.orElseGet { null }
}