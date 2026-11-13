package net.bestia.zone.worldboss

import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.spawn.ecs.DeathSystem
import net.bestia.zone.spawn.ecs.DenMember
import net.bestia.zone.spawn.ecs.Spawner
import org.springframework.stereotype.Component as SpringComponent

/** Notices a lair boss that players killed, before [DeathSystem] destroys its body. */
@SpringComponent
class WorldBossSlainSystem(
  private val lairs: WorldBossLairService,
) : System {

  override val phase = Phase.DEATH
  override val after = setOf(PlayerDeathSystem::class)
  override val before = setOf(DeathSystem::class)

  override val reads: ComponentClassSet = setOf(Dead::class, DenMember::class, TakenDamage::class, Account::class)

  // The lair's den is destroyed.
  override val writes: ComponentClassSet = setOf(Spawner::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, DenMember::class).each { id ->
      val lair = get<DenMember>().den.featureId
      if (!lairs.isLair(lair)) return@each

      val slayer = world.get(id, TakenDamage::class)?.topAccount(world) ?: return@each
      lairs.slainByPlayers(world, lair, slayer)
    }
  }
}
