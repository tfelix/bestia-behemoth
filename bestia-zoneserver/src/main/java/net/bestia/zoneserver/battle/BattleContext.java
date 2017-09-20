package net.bestia.zoneserver.battle;

import java.util.Objects;

import net.bestia.entity.Entity;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusValues;
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
		private Entity defender;
		private int distance;

		private StatusPoints attackerStatusPoints;
		private StatusPoints defenderStatusPoints;
		private StatusBasedValues attackerStatusBased;
		private StatusBasedValues defenderStatusBased;
		private StatusValues attackerStatusValues;
		private StatusValues defenderStatusValues;

		public Builder(Attack usedAttack, Entity attacker) {

			this.usedAttack = Objects.requireNonNull(usedAttack);
			this.attacker = Objects.requireNonNull(attacker);
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

	}

	private final Attack usedAttack;
	private final Entity attacker;

	private final Entity defender;
	private final int distance;

	private final StatusPoints attackerStatusPoints;
	private final StatusPoints defenderStatusPoints;
	private final StatusBasedValues attackerStatusBased;
	private final StatusBasedValues defenderStatusBased;
	private final StatusValues attackerStatusValues;
	private final StatusValues defenderStatusValues;

	private BattleContext(Builder builder) {

		this.usedAttack = builder.usedAttack;
		this.attacker = builder.attacker;

		this.defender = builder.defender;
		this.distance = builder.distance;

		this.attackerStatusPoints = builder.attackerStatusPoints;
		this.defenderStatusPoints = builder.defenderStatusPoints;
		this.attackerStatusBased = builder.attackerStatusBased;
		this.defenderStatusBased = builder.defenderStatusBased;
		this.attackerStatusValues = builder.attackerStatusValues;
		this.defenderStatusValues = builder.defenderStatusValues;
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

	public int getDistance() {
		return distance;
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

	public StatusValues getAttackerStatusValues() {
		return attackerStatusValues;
	}

	public StatusValues getDefenderStatusValues() {
		return defenderStatusValues;
	}
}
