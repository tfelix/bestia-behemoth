package net.bestia.zoneserver.battle;

import java.util.Objects;

import net.bestia.entity.Entity;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.ConditionValues;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.entity.StatusBasedValues;

/**
 * Data transfer object to carry all needed data during a damage calculation.
 * 
 * @author Thomas Felix
 *
 */
public final class BattleContext {

	public static final class Builder {

		private final Attack usedAttack;
		private final Entity attacker;
		private final DamageVariables damageVariables;
		
		private Entity defender;
		
		private StatusPoints attackerStatusPoints;
		private StatusPoints defenderStatusPoints;
		
		private StatusBasedValues attackerStatusBased;
		private StatusBasedValues defenderStatusBased;
		
		private ConditionValues attackerCondValues;
		private ConditionValues defenderCondValues;

		public Builder(Attack usedAttack, Entity attacker, DamageVariables dmgVars) {

			this.usedAttack = Objects.requireNonNull(usedAttack);
			this.attacker = Objects.requireNonNull(attacker);
			this.damageVariables = Objects.requireNonNull(dmgVars);
		}

		public BattleContext build() {
			return new BattleContext(this);
		}

		public Builder setAttackerStatus(StatusPoints atkStatus) {
			this.attackerStatusPoints = atkStatus;
			return this;
		}

		public Builder setDefenderStatus(StatusPoints defStatus) {
			this.defenderStatusPoints = defStatus;
			return this;
		}

		public Builder setAttackerBasedValues(StatusBasedValues atkStatusBased) {
			this.attackerStatusBased = atkStatusBased;
			return this;
		}
		
		public Builder setDefenderBasedValues(StatusBasedValues defStatusBased) {
			this.defenderStatusBased = defStatusBased;
			return this;
		}

		public Builder setAttackerCondition(ConditionValues atkCond) {
			this.attackerCondValues = atkCond;
			return this;
		}

		public Builder setDefenderCondition(ConditionValues defCond) {
			this.defenderCondValues = defCond;
			return this;
		}

	}

	private final Attack usedAttack;
	private final Entity attacker;
	private final DamageVariables damageVariables;

	private final Entity defender;

	private final StatusPoints attackerStatusPoints;
	private final StatusPoints defenderStatusPoints;
	private final StatusBasedValues attackerStatusBased;
	private final StatusBasedValues defenderStatusBased;
	private final ConditionValues attackerCondtitionValues;
	private final ConditionValues defenderConditionValues;

	private BattleContext(Builder builder) {

		this.usedAttack = builder.usedAttack;
		this.attacker = builder.attacker;
		this.damageVariables = builder.damageVariables;

		this.defender = builder.defender;

		this.attackerStatusPoints = builder.attackerStatusPoints;
		this.defenderStatusPoints = builder.defenderStatusPoints;
		this.attackerStatusBased = builder.attackerStatusBased;
		this.defenderStatusBased = builder.defenderStatusBased;
		this.attackerCondtitionValues = builder.attackerCondValues;
		this.defenderConditionValues = builder.defenderCondValues;
	}

	public Attack getUsedAttack() {
		return usedAttack;
	}

	public Entity getAttacker() {
		return attacker;
	}

	public Entity getDefender() {
		return defender;
	}
	
	public DamageVariables getDamageVariables() {
		return damageVariables;
	}

	public StatusPoints getAttackerStatusPoints() {
		return attackerStatusPoints;
	}

	public StatusPoints getDefenderStatusPoints() {
		return defenderStatusPoints;
	}

	public StatusBasedValues getAttackerStatusBased() {
		return attackerStatusBased;
	}

	public StatusBasedValues getDefenderStatusBased() {
		return defenderStatusBased;
	}

	public ConditionValues getAttackerCondition() {
		return attackerCondtitionValues;
	}
	
	public ConditionValues getDefenderCondition() {
		return defenderConditionValues;
	}

	public Element getDefenderElement() {
		// TODO Auto-generated method stub
		return null;
	}

	public Element getAttackElement() {
		// TODO Auto-generated method stub
		return null;
	}

	public int getAttackerLevel() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getDefenderLevel() {
		// TODO Auto-generated method stub
		return 0;
	}
}
