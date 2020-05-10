package net.bestia.model.battle

import net.bestia.model.AbstractEntity
import net.bestia.model.bestia.PlayerBestia
import java.io.Serializable
import javax.persistence.Entity
import javax.persistence.ManyToOne
import javax.persistence.Table

/**
 * Lists the default attacks a Bestia will learn when it gains more
 * level.
 *
 * @author Thomas Felix
 */
@Entity
@Table
class PlayerBestiaAttack(
    @ManyToOne
    val attack: Attack,

    @ManyToOne
    val playerBestia: PlayerBestia,

    /**
     * Returns the minimum level required until the Bestia can use this attack.
     *
     * @return The minimum level until the Bestia can use this attack.
     */
    val minLevel: Int = 0
) : AbstractEntity(), Serializable {

  override fun toString(): String {
    return "PlayerAttack[attack_db_name: ${attack.databaseName}, pbId: ${playerBestia.id}, minLevel: $minLevel]"
  }
}
