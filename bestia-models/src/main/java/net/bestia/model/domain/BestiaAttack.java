package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.bestia.model.battle.Attack;
import net.bestia.model.battle.AttackImpl;
import net.bestia.model.bestia.Bestia;

/**
 * Gives a clue which bestia learns which attack at a certain level. Usually
 * this is only defined in the database. Hence there are no setters.
 * 
 * @author Thomas Felix
 *
 */
@Entity
@Table(name = "bestia_attacks", uniqueConstraints = { @UniqueConstraint(columnNames = {
		"ATTACK_ID", "BESTIA_ID" }) })
public class BestiaAttack implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false)
	@JsonIgnore
	private int id;

	@ManyToOne
	@JoinColumn(name = "ATTACK_ID", nullable = false)
	@JsonProperty("a")
	private AttackImpl attack;

	@ManyToOne
	@JoinColumn(name = "BESTIA_ID", nullable = false)
	@JsonIgnore
	private Bestia bestia;

	@JsonProperty("mlv")
	private int minLevel;

	/**
	 * Std. Ctor.
	 */
	public BestiaAttack() {
		// no op.
	}

	@Override
	public String toString() {
		return String.format("BestiaAttack[attack_db_name: %s, minLevel: %d]", attack.getDatabaseName(), minLevel);
	}

	/**
	 * The attack which can be used after the level requirement was fullfilled.
	 * 
	 * @return The attack to be used.
	 */
	public Attack getAttack() {
		return attack;
	}

	public Bestia getBestia() {
		return bestia;
	}

	/**
	 * Returns the minimum level required until the bestia can use this attack.
	 * 
	 * @return The minimum level until the bestia can use this attack.
	 */
	public int getMinLevel() {
		return minLevel;
	}
}
