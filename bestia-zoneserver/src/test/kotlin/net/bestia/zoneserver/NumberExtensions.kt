package net.bestia.zoneserver

import java.time.Duration

internal val Number.ms: Duration
  get() = Duration.ofSeconds(this.toLong())

internal val Number.seconds: Duration
  get() = Duration.ofSeconds(this.toLong())