package net.bestia.zoneserver.battle;

import java.util.Objects;
import java.util.Random;

import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.model.battle.Damage;
import net.bestia.model.battle.Damage.DamageType;
import net.bestia.model.domain.Attack;

@Component
public final class DamageCalculator {

	//private EntityService entityService;
	private Random rand = new Random();

	/**
	 * No instance is needed. The {@link DamageCalculator} can be used entirely
	 * in a static way.
	 */
	public DamageCalculator(EntityService entityService) {

		//this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * Calculates the damage if an attack hits a target entity. This calculates
	 * damage as long as the target entity is able to participate in the damage
	 * system. This means it must have a status component attached.
	 * 
	 * If the target entity does not fulfill the requirements of getting a
	 * damage calculated it will throw {@link IllegalArgumentException}.
	 * 
	 * NOTE: No damage will be applied to the entity. This is in the
	 * responsibility of the caller.
	 * 
	 * <p>
	 * Note: The implementation of the damage formula is not COMPLETE!
	 * </p>
	 * 
	 * @param attack
	 * @param user
	 * @param target
	 * @return The damage the entity would take.
	 */
	public Damage calculate(Attack attack, Entity user, Entity target) {
		Objects.requireNonNull(attack);
		Objects.requireNonNull(user);
		Objects.requireNonNull(target);

		return new Damage(1 + rand.nextInt(5), DamageType.HIT);
	}

}
