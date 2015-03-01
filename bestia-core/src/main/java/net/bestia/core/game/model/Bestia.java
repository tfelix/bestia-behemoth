package net.bestia.core.game.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.AttributeOverrides;
import javax.persistence.AttributeOverride;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonIgnore;

import net.bestia.core.game.battle.AttackBasedStatus;
import net.bestia.core.game.battle.Element;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Bestia {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int id;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "hp", column = @Column(name = "bHp")),
			@AttributeOverride(name = "mana", column = @Column(name = "bMana")),
			@AttributeOverride(name = "atk", column = @Column(name = "bAtk")),
			@AttributeOverride(name = "def", column = @Column(name = "bDef")),
			@AttributeOverride(name = "spAtk", column = @Column(name = "bSpAtk")),
			@AttributeOverride(name = "spDef", column = @Column(name = "bSpDef")),
			@AttributeOverride(name = "spd", column = @Column(name = "bSpd")) })
	@JsonIgnore
	private BaseValues baseValue;
	@JsonIgnore
	private String databaseName;
	@Enumerated(EnumType.STRING)
	private Element element;
	private int level;
	@OneToMany
	@JsonIgnore
	private Set<Attack> attacks;
	private StatusPoints statusPoints;

	public StatusPoints getStatusPoints() {
		return statusPoints;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		if (level < 0 || level > 100) {
			throw new IllegalArgumentException(
					"Level must be in the range between 0 and 100.");
		}
		this.level = level;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public Set<Attack> getAttacks() {
		return new HashSet<Attack>(attacks);
	}

	/**
	 * Sets the attacks of a bestia. A bestia can not learn more then 4 attacks
	 * + one attack which does only heal/buff effects.
	 * 
	 * @param attacks
	 */
	public void setAttacks(Set<Attack> attacks) {
		boolean hasBuff = false;
		for (Attack atk : attacks) {
			if (atk.getBasedStatus() == AttackBasedStatus.NO_DAMAGE) {
				hasBuff = true;
				break;
			}
		}
		if ((hasBuff && attacks.size() > 5) || (!hasBuff && attacks.size() > 4)) {
			throw new IllegalArgumentException(
					"Bestia can only learn 4 attacks. Or 4 Attacks + 1 NO_DAMAGE Attack.");
		}
		this.attacks = attacks;
	}
}
