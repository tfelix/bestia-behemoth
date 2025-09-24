package net.bestia.zone.util

fun requireValidIdentifier(value: String) {
  require(value.matches(Regex("^[A-Za-z_]+$"))) {
    "Identifier $value must contain only ASCII letters or underscores"
  }
}