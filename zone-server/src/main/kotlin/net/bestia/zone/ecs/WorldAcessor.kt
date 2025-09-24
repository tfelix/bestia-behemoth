package net.bestia.zone.ecs

import com.github.quillraven.fleks.World

interface WorldAcessor {
  fun doWithWorld(world: World)
}

