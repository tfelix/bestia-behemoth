package net.bestia.model.battle

import java.io.Serializable

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table

import com.fasterxml.jackson.annotation.JsonProperty

@Entity
@Table(name = "attacks")
class AttackImpl(
    @Id
    override val id: Int = 0,

    @Column(name = "attack_db_name", unique = true, nullable = false)
    @JsonProperty("adbn")
    override val databaseName: String,

    @JsonProperty("str")
    override val strength: Int,

    @Enumerated(EnumType.STRING)
    @JsonProperty("ele")
    @Column(nullable = false)
    override val element: Element,

    @JsonProperty("m")
    override val manaCost: Int = 0,

    /**
     * Flag tells if the attack has a script which needs to get executed upon
     * execution.
     */
    override val hasScript: Boolean = false,

    /**
     * Range of the attack. Range is a mysql reserved word, so quots are needed.
     */
    @JsonProperty("r")
    @Column(name = "atkRange", nullable = false)
    override var range: Int = 0,

    /**
     * Check if a line of sight to the target is necessairy.
     */
    @JsonProperty("los")
    override val needsLineOfSight: Boolean = false,

    @Enumerated(EnumType.STRING)
    @JsonProperty("ty")
    override val type: AttackType,

    /**
     * Casttime in ms. 0 means it is instant.
     */
    @JsonProperty("ct")
    override val casttime: Int = 0,

    @JsonProperty("cd")
    override var cooldown: Int = 0,

    /**
     * Shows if there is a special indicator when the attack is activated to be
     * shown.
     */
    @JsonProperty("i")
    override val indicator: String? = null,

    @JsonProperty("a")
    val animation: String? = null,

    @Enumerated(EnumType.STRING)
    @JsonProperty("t")
    override var target: AttackTarget

) : Serializable, Attack {

  companion object {
    val DFAULT_MELEE_ATTACK: Attack = AttackImpl(
        id = Attack.DEFAULT_MELEE_ATTACK_ID,
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
