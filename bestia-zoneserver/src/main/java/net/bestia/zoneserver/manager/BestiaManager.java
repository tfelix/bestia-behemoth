package net.bestia.zoneserver.manager;

import java.util.HashMap;
import java.util.Map;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.StatusPoints;

public abstract class BestiaManager {

	protected boolean hasChanged = false;
	private Map<Integer, Long> attackUsageTimer = new HashMap<>();

	public abstract StatusPoints getStatusPoints();

	public abstract Location getLocation();

	public abstract int getLevel();

	public float getManaRegenerationRate() {
		final StatusPoints statusPoints = getStatusPoints();
		final int level = getLevel();
		final float regen = (statusPoints.getDef() * 2
				+ statusPoints.getSpDef() * 4 + level / 2) / 100.0f;
		return regen;
	}

	public BestiaManager() {

	}

	public boolean hasChanged() {
		return hasChanged || getLocation().hasChanged()
				|| getStatusPoints().hasChanged();
	}

	public void resetChanged() {
		hasChanged = false;
		getLocation().resetChanged();
		getStatusPoints().resetChanged();
	}

	/**
	 * Tries to use this attack. The usage of the attack will fail if there is
	 * not enough mana to use it or if the cooldown for this attack is still
	 * ticking. If the atk is null it wont execute and return false.
	 * <p>
	 * Note: There are NO checks if the bestia actually owns this attack. This
	 * is especially for NPC bestias so they can use all attacks they like via
	 * scripts for example.
	 * </p>
	 * 
	 * @param atk
	 *            Attack to be used.
	 */
	public boolean useAttack(Attack atk) {

		if (atk == null) {
			return false;
		}

		final StatusPoints sp = getStatusPoints();

		// Check available mana.
		if (sp.getCurrentMana() < atk.getManaCost()) {
			return false;
		}

		final long curTime = System.currentTimeMillis();
		final long attackCooldownTime;
		if (attackUsageTimer.containsKey(atk.getId())) {
			attackCooldownTime = attackUsageTimer.get(atk.getId())
					+ atk.getCooldown();
		} else {
			attackCooldownTime = 0;
		}

		if (curTime < attackCooldownTime) {
			return false;
		}

		// Use the attack.
		sp.setCurrentMana(sp.getCurrentMana() - atk.getManaCost());
		attackUsageTimer.put(atk.getId(), curTime);

		return true;
	}
}