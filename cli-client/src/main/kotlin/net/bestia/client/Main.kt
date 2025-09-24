package net.bestia.client

import net.bestia.client.command.Session


fun main() {
  Session().use { session ->
    session.start()
  }
}