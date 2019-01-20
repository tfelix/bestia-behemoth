package net.bestia.model.battle

import java.io.Serializable

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.Transient
import javax.persistence.UniqueConstraint

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.model.AbstractEntity
import net.bestia.model.battle.Attack
import net.bestia.model.battle.AttackImpl
import net.bestia.model.bestia.Bestia

/**
 * Lists the default attacks a Bestia will learn when it gains more
 * level.
 *
 * @author Thomas Felix
 */
/**
 * Std. Ctor.
 */
@Entity
@Table(name = "bestia_attacks", uniqueConstraints = [
  UniqueConstraint(columnNames = arrayOf("ATTACK_ID", "BESTIA_ID"))
])
class BestiaAttack(
    @ManyToOne
    @JoinColumn(name = "ATTACK_ID", nullable = false)
    @JsonProperty("a")
    val attack: Attack,

    @ManyToOne
    @JoinColumn(name = "BESTIA_ID", nullable = false)
    @JsonIgnore
    val bestia: Bestia,

    /**
     * Returns the minimum level required until the bestia can use this attack.
     *
     * @return The minimum level until the bestia can use this attack.
     */
    @JsonProperty("mlv")
    val minLevel: Int = 0
) : AbstractEntity(), Serializable {

  override fun toString(): String {
    return "BestiaAttack[attack_db_name: ${attack.databaseName}, bestia: ${bestia.databaseName}, minLevel: $minLevel]"
  }
}
