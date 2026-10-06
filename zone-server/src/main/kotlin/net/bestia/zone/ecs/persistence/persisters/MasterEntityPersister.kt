package net.bestia.zone.ecs.persistence.persisters

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.skill.MasterSkillTreeRegistry
import net.bestia.zone.identity.ecs.Master as MasterComponent
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.EntityPersister
import net.bestia.zone.persistence.EntitySnapshot
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.LearnedSkill
import net.bestia.zone.skill.LearnedSkillRepository
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Mutable master state written back to the dedicated relational `master` table. */
data class MasterSnapshot(
  override val entityId: EntityId,
  val masterId: Long,
  val x: Long,
  val y: Long,
  val z: Long,
  val level: Int,
  val exp: Int,
  val skillPoints: Int,
  val statusPoints: Int,
  val strength: Int,
  val vitality: Int,
  val intelligence: Int,
  val dexterity: Int,
  val willpower: Int,
  val agility: Int,
  val currentHealth: Int?,
  /** Left the world dead, so the write-back resolves the respawn instead of storing where it fell. */
  val died: Boolean,
  /** Skill id to invested level, from `KnownSkills`. */
  val learnedSkills: Map<Long, Int>,
) : EntitySnapshot {

  /** Keyed like every other write about this master, inventory included, so they land in order. */
  override val writeKey: Any
    get() {
      return masterId
    }
}

/**
 * Persists online player masters back into their `master` row and their learned skill levels. The only writer
 * of those columns while the master is online: a spend changes the components and lands here like any other
 * change. Masters are loaded on login via `MasterEntitySpawner`, so this persister does not participate in
 * startup rehydration ([loadsAtStartup] = false).
 */
@Component
class MasterEntityPersister(
  private val masterRepository: MasterRepository,
  private val learnedSkillRepository: LearnedSkillRepository,
  private val skillRepository: SkillRepository,
  private val skillTree: MasterSkillTreeRegistry,
) : EntityPersister {

  override val kind = "master"
  override val loadsAtStartup = false

  override fun supports(world: World, id: EntityId): Boolean =
    world.has(id, MasterComponent::class)

  override fun snapshot(world: World, id: EntityId): EntitySnapshot? {
    val master = world.get(id, MasterComponent::class) ?: return null
    val pos = world.get(id, Position::class) ?: return null
    val level = world.get(id, Level::class)?.level ?: 1
    val exp = world.get(id, Exp::class)?.value ?: 0
    val skillPoints = world.get(id, SkillPoints::class)?.value ?: 0
    val statusPoints = world.get(id, StatusPoints::class)?.value ?: 0
    val baseStatusValues = world.get(id, BaseStatusValues::class)
    return MasterSnapshot(
      entityId = id,
      masterId = master.masterId,
      x = pos.x, y = pos.y, z = pos.z,
      level = level,
      exp = exp,
      skillPoints = skillPoints,
      statusPoints = statusPoints,
      strength = baseStatusValues?.strength ?: 10,
      vitality = baseStatusValues?.vitality ?: 10,
      intelligence = baseStatusValues?.intelligence ?: 10,
      dexterity = baseStatusValues?.dexterity ?: 10,
      willpower = baseStatusValues?.willpower ?: 10,
      agility = baseStatusValues?.agility ?: 10,
      currentHealth = world.get(id, Health::class)?.current,
      died = world.has(id, Dead::class),
      learnedSkills = world.get(id, KnownSkills::class)?.levels().orEmpty(),
    )
  }

  @Transactional
  override fun persist(snapshots: List<EntitySnapshot>) {
    // Updated in id order: the rows stay locked until commit, and trade settlement locks masters in id order too.
    for (snap in snapshots.map { it as MasterSnapshot }.sortedBy { it.masterId }) {
      // Locked: inventory and party writes outside the write-behind still touch the same row.
      val master = masterRepository.findByIdForUpdate(snap.masterId)
      if (master == null) {
        LOG.warn { "Master ${snap.masterId} was not found, cannot persist it" }
        continue
      }
      if (snap.died) {
        // Dying and then leaving resolves the respawn on the way out, so the player does not
        // re-materialise on top of whatever killed them - and cannot sit out the death by quitting.
        master.currentPosition = master.spawnPosition
        master.currentHealth = 1
      } else {
        master.currentPosition = Vec3L(snap.x, snap.y, snap.z)
        master.currentHealth = snap.currentHealth
      }
      master.level = snap.level
      master.exp = snap.exp
      master.skillPoints = snap.skillPoints
      master.statusPoints = snap.statusPoints
      master.strength = snap.strength
      master.vitality = snap.vitality
      master.intelligence = snap.intelligence
      master.dexterity = snap.dexterity
      master.willpower = snap.willpower
      master.agility = snap.agility
      masterRepository.save(master)
      saveLearnedSkills(master, snap.learnedSkills)
      LOG.debug { "Persisted master ${master.id} at ${master.currentPosition} (level ${master.level})" }
    }
  }

  /** Never deletes a level, and skips a skill the tree no longer has, whose row would only fail its foreign key. */
  private fun saveLearnedSkills(master: Master, levels: Map<Long, Int>) {
    if (levels.isEmpty()) return

    val stored = learnedSkillRepository.findAllByMasterId(master.id).associateBy { it.skill.id }

    for ((skillId, level) in levels) {
      if (skillTree.findBySkillId(skillId) == null) continue

      val row = stored[skillId]
      if (row == null) {
        learnedSkillRepository.save(LearnedSkill(skill = skillRepository.getReferenceById(skillId), level = level, master = master))
      } else if (row.level != level) {
        row.level = level
        learnedSkillRepository.save(row)
      }
    }
  }

  /** Masters are rehydrated on demand at login, not at startup. */
  override fun loadAll(world: World) = Unit

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** What [snapshot] reads; a system that snapshots a master must declare these. */
    val SNAPSHOT_READS: ComponentClassSet = setOf(
      MasterComponent::class, Position::class, Level::class, Exp::class, SkillPoints::class,
      StatusPoints::class, BaseStatusValues::class, Health::class, Dead::class, KnownSkills::class,
    )
  }
}
