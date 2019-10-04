package net.bestia.model.battle

import java.io.Serializable
import javax.persistence.*

@Entity
@Table(name = "attacks")
class Attack(
    @Id
    val id: Int = 0,

    @Column(name = "attack_db_name", unique = true, nullable = false)
    val databaseName: String,

    val strength: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val element: Element,

     val manaCost: Int = 0,

    /**
     * Flag tells if the attack has a script which needs to get executed upon
     * execution.
     */
    val hasScript: Boolean = false,

    /**
     * Range of the attack. Range is a mysql reserved word so an alias is needed.
     * Range is in meter.
     */
    @Column(name = "atkRange", nullable = false)
    val range: Int = 0,

    /**
     * Check if a line of sight to the target is necessairy.
     */
    val needsLineOfSight: Boolean = false,

    @Enumerated(EnumType.STRING)
    val type: AttackType,

    /**
     * Casttime in ms. 0 means it is instant.
     */
    val casttime: Int = 0,

    val cooldown: Int = 0,

    @Enumerated(EnumType.STRING)
    val target: AttackTarget
) : Serializable {

  val isRanged: Boolean
    get() = type == AttackType.RANGED_MAGIC || type == AttackType.RANGED_PHYSICAL

  /**
   * @return TRUE if the attack is magic or FALSE if its physical.
   */
  val isMagic: Boolean
    get() = type == AttackType.RANGED_MAGIC || type == AttackType.MELEE_MAGIC

  companion object {
    /**
     * Basic attack id used for the default attack every bestia has. Each bestia
     * has the default melee or ranged attack.
     */
    const val DEFAULT_MELEE_ATTACK_ID = -1
    const val DEFAULT_RANGE_ATTACK_ID = -2
    val DEFAULT_MELEE_ATTACK: Attack = Attack(
        id = DEFAULT_MELEE_ATTACK_ID,
        databaseName = "default_melee_attack",
        strength = 5,
        element = Element.NORMAL,
        manaCost = 0,
        range = 1,
        needsLineOfSight = true,
        type = AttackType.MELEE_PHYSICAL,
        target = AttackTarget.ENEMY_ENTITY,
        casttime = 0,
        cooldown = 1500
    )
  }
}
