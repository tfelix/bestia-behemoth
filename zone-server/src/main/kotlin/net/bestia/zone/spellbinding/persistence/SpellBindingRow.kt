package net.bestia.zone.spellbinding.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.casting.InscribedSpell
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.spellbinding.BinderSnapshot
import net.bestia.zone.spellbinding.BindingFilter
import net.bestia.zone.spellbinding.BindingTrigger
import net.bestia.zone.spellbinding.BoundSpell
import net.bestia.zone.spellbinding.SpellBindingOwner
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * One Spell Binding waiting for its trigger. It has no timer, so the row is what keeps it through a restart and
 * while its binder is away. A ground binding has a position, an item binding an item instance.
 */
@Entity
@Table(
  name = "spell_binding",
  indexes = [Index(name = "idx_spell_binding_master", columnList = "master_id")]
)
class SpellBindingRow(
  /** Handed out by the tick from [net.bestia.zone.spellbinding.SpellBindingBook], so a binding has its id before its row is written. */
  @Id
  val id: Long,

  @Column(name = "master_id", nullable = false)
  val masterId: Long,

  @Column(name = "account_id", nullable = false)
  val accountId: Long,

  @Column(name = "master_name", nullable = false, length = 40)
  val masterName: String,

  @Column(name = "skill_id", nullable = false)
  val skillId: Long,

  @Column(nullable = false)
  val level: Int,

  @Column(name = "attack_level", nullable = false)
  val attackLevel: Int,

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "trigger_kind", nullable = false, length = 24)
  val trigger: BindingTrigger,

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 24)
  val filter: BindingFilter,

  @Column(nullable = false)
  val potency: Int,

  @Column(name = "bound_at", nullable = false)
  val boundAt: Instant,

  @Column(name = "fires_at")
  val firesAt: Instant?,

  @Column(name = "pos_x")
  val x: Long?,

  @Column(name = "pos_y")
  val y: Long?,

  @Column(name = "pos_z")
  val z: Long?,

  @Column(name = "item_instance_id")
  val itemInstanceId: Long?,

  @Column(name = "binder_level", nullable = false)
  val binderLevel: Int,

  @Column(nullable = false)
  val strength: Int,

  @Column(nullable = false)
  val vitality: Int,

  @Column(nullable = false)
  val intelligence: Int,

  @Column(nullable = false)
  val agility: Int,

  @Column(nullable = false)
  val willpower: Int,

  @Column(nullable = false)
  val dexterity: Int,

  @Column(nullable = false)
  val attack: Int,

  @Column(name = "magic_attack", nullable = false)
  val magicAttack: Int,

  @Column(name = "weapon_upgrade_level", nullable = false)
  val weaponUpgradeLevel: Int,
) {

  val position: Vec3L?
    get() {
      val x = x ?: return null
      val y = y ?: return null
      val z = z ?: return null

      return Vec3L(x, y, z)
    }

  fun toBoundSpell(): BoundSpell {
    return BoundSpell(
      id = id,
      owner = SpellBindingOwner(accountId, masterId, masterName),
      spell = InscribedSpell(skillId, level, attackLevel),
      trigger = trigger,
      filter = filter,
      potency = potency,
      binder = BinderSnapshot(
        level = binderLevel,
        attributes = StatusValues(strength, vitality, intelligence, agility, willpower, dexterity),
        attack = attack,
        magicAttack = magicAttack,
        weaponUpgradeLevel = weaponUpgradeLevel,
      ),
      boundAt = boundAt,
      firesAt = firesAt,
    )
  }

  companion object {
    fun of(bound: BoundSpell, position: Vec3L?, itemInstanceId: Long?): SpellBindingRow {
      val attributes = bound.binder.attributes

      return SpellBindingRow(
        id = bound.id,
        masterId = bound.owner.masterId,
        accountId = bound.owner.accountId,
        masterName = bound.owner.masterName,
        skillId = bound.spell.skillId,
        level = bound.spell.level,
        attackLevel = bound.spell.attackLevel,
        trigger = bound.trigger,
        filter = bound.filter,
        potency = bound.potency,
        boundAt = bound.boundAt,
        firesAt = bound.firesAt,
        x = position?.x,
        y = position?.y,
        z = position?.z,
        itemInstanceId = itemInstanceId,
        binderLevel = bound.binder.level,
        strength = attributes.strength,
        vitality = attributes.vitality,
        intelligence = attributes.intelligence,
        agility = attributes.agility,
        willpower = attributes.willpower,
        dexterity = attributes.dexterity,
        attack = bound.binder.attack,
        magicAttack = bound.binder.magicAttack,
        weaponUpgradeLevel = bound.binder.weaponUpgradeLevel,
      )
    }
  }
}
