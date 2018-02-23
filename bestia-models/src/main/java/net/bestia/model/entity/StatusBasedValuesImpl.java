package bestia.model.entity;

import java.io.Serializable;
import java.util.Objects;

import bestia.model.domain.StatusPoints;
import bestia.model.map.Walkspeed;

/**
 * These modifier are calculates based on status values. The are used to
 * calculate various aspects of the game.
 * 
 * @author Thomas Felix
 *
 */
public class StatusBasedValuesImpl implements Serializable, StatusBasedValues {

	private static final long serialVersionUID = 1L;
	
	private final StatusPoints status;
	private int level;

	public StatusBasedValuesImpl(StatusPoints status, int level) {
		if (level < 1) {
			throw new IllegalArgumentException("Level can not be smaller then 1.");
		}

		this.status = Objects.requireNonNull(status);
		this.level = level;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#setLevel(int)
	 */
	@Override
	public void setLevel(int level) {
		this.level = level;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getHpRegenRate()
	 */
	@Override
	public float getHpRegenRate() {

		final float hpRegen = (status.getVitality() * 4 + level) / 100.0f;
		return hpRegen;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getManaRegenRate()
	 */
	@Override
	public float getManaRegenRate() {

		final float manaRegen = (status.getVitality() * 1.5f + level) / 100.0f;
		return manaRegen;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getCriticalHitrate()
	 */
	@Override
	public int getCriticalHitrate() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getDodge()
	 */
	@Override
	public int getDodge() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getCasttime()
	 */
	@Override
	public float getCasttimeMod() {
		return 1;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getCastduration()
	 */
	@Override
	public float getSpellDurationMod() {
		return 1;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getHitrate()
	 */
	@Override
	public int getHitrate() {
		return 1000;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getMinDamage()
	 */
	@Override
	public int getMinDamage() {
		return 60;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getRangedBonusDamage()
	 */
	@Override
	public int getRangedBonusDamage() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getAttackSpeed()
	 */
	@Override
	public float getAttackSpeed() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see net.bestia.model.entity.IStatusBasedValues#getWalkspeed()
	 */
	@Override
	public Walkspeed getWalkspeedMod() {
		return Walkspeed.fromFloat(1);
	}

	@Override
	public float getCooldownMod() {
		// TODO Auto-generated method stub
		return 1.0f;
	}
	
	@Override
	public String toString() {
		return String.format("StatBasedValues[]");
	}
}
