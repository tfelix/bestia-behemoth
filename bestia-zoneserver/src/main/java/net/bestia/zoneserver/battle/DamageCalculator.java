package net.bestia.zoneserver.battle;

import java.util.Objects;

import org.springframework.stereotype.Service;

import net.bestia.model.battle.Damage;
import net.bestia.model.domain.AttackImpl;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.components.LevelComponent;
import net.bestia.zoneserver.entity.components.StatusComponent;
import net.bestia.model.domain.AttackBasedStatus;

@Service
public final class DamageCalculator {

	private EntityService entityService;

	/**
	 * No instance is needed. The {@link DamageCalculator} can be used entirely
	 * in a static way.
	 */
	public DamageCalculator(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * Calculates the damage if an attack hits a target. NOTE: No damage will be
	 * applied to the entity. This is in the responsibility of the caller.
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
	/*
	public Damage calculate(AttackImpl attack, Entity user, Entity target) {
		Objects.requireNonNull(attack);
		Objects.requireNonNull(user);
		Objects.requireNonNull(target);

		final float atkV;
		final float defV;

		final int userLevel = entityService.getComponent(user, LevelComponent.class)
				.map(LevelComponent::getLevel)
				.orElse(10);

		if (attack.getBasedStatus() == AttackBasedStatus.NORMAL) {
			atkV = user.getStatusPoints().getStrength();
			defV = target.getStatusPoints().getDefense();
		} else {
			atkV = user.getStatusPoints().getIntelligence();
			defV = target.getStatusPoints().getMagicDefense();
		}

		// Calculate base damage.
		float dmg = 2.0f * userLevel + 10 / 250 * (atkV / defV) * attack.getStrength() + 2;

		// Calculate all the needed modifier.

		// same type as attack.
		final float stabMod = (attack.getElement() == user.getElement()) ? 1.3f : 1.0f;
		final float sizeMod = 1.0f;

		final float mods = stabMod * sizeMod;

		dmg *= mods;

		final Damage damage = Damage.getHit((int) dmg);

		return damage;
	}*/

}
