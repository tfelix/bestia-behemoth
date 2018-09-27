package net.bestia.model.battle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import net.bestia.model.entity.StatusBasedValues;
import net.bestia.model.map.Walkspeed;

/**
 * This decorator decorates a {@link StatusBasedValues} and is able to change
 * these values by attaching {@link StatusBasedValueModifier} to it.
 * 
 * @author Thomas Felix
 *
 */
public class StatusBasedValuesDecorator implements StatusBasedValues, Serializable {

	private static final long serialVersionUID = 1L;

	private final StatusBasedValues wrapped;

	private List<StatusBasedValueModifier> statusMods = new ArrayList<>();

	public StatusBasedValuesDecorator(StatusBasedValues wrapped) {

		this.wrapped = Objects.requireNonNull(wrapped);
	}

	/**
	 * Adds a new status based value modifier.
	 * 
	 * @param mod
	 *            The new modifier to be added.
	 */
	public void addStatusModifier(StatusBasedValueModifier mod) {
		statusMods.add(Objects.requireNonNull(mod));
	}

	/**
	 * Removes the given modifier.
	 * 
	 * @param mod
	 *            The modifier to be removed.
	 */
	public void removeStatusModifier(StatusBasedValueModifier mod) {
		statusMods.remove(mod);
	}

	/**
	 * Removes all added modfier.
	 */
	public void clearModifier() {
		statusMods.clear();
	}

	@Override
	public void setLevel(int level) {
		wrapped.setLevel(level);
	}

	/**
	 * Shortcut to sum up an floating point returning function.
	 * 
	 * @param func
	 *            Float returning function.
	 * @return Returns the sum.
	 */
	private float sumFloat(ToDoubleFunction<? super StatusBasedValueModifier> func) {

		if (statusMods.size() == 0) {
			return 1f;
		}

		return (float) statusMods.stream()
				.mapToDouble(func)
				.reduce(1, (a, b) -> a * b);
	}

	/**
	 * Shortcut to sum up an integer returning function.
	 * 
	 * @param func
	 *            Int returning function.
	 * @return Returns the sum.
	 */
	private int sumInt(ToIntFunction<? super StatusBasedValueModifier> func) {

		return statusMods.stream()
				.mapToInt(func)
				.sum();
	}

	@Override
	public float getHpRegenRate() {

		final float mod = sumFloat(StatusBasedValueModifier::getHpRegenMod);
		final int value = sumInt(StatusBasedValueModifier::getHpRegenValue);

		return wrapped.getHpRegenRate() * mod + value;
	}

	@Override
	public float getManaRegenRate() {
		
		final float mod = sumFloat(StatusBasedValueModifier::getManaRegenMod);
		final int value = sumInt(StatusBasedValueModifier::getManaRegenValue);

		return wrapped.getManaRegenRate() * mod + value;
	}

	@Override
	public int getCriticalHitrate() {
		
		final float mod = sumFloat(StatusBasedValueModifier::getCritMod);
		final int value = sumInt(StatusBasedValueModifier::getCritValue);

		return Math.round(wrapped.getCriticalHitrate() * mod) + value;
	}

	@Override
	public int getDodge() {
		
		final float mod = sumFloat(StatusBasedValueModifier::getDodgeMod);
		final int value = sumInt(StatusBasedValueModifier::getDodgeValue);

		return Math.round(wrapped.getDodge() * mod) + value;
	}

	@Override
	public float getCasttimeMod() {
		
		final float mod = sumFloat(StatusBasedValueModifier::getDodgeMod);
		final int value = sumInt(StatusBasedValueModifier::getDodgeValue);

		return Math.round(wrapped.getDodge() * mod) + value;
	}

	@Override
	public float getSpellDurationMod() {
		
		final float mod = sumFloat(StatusBasedValueModifier::getCastDurationMod);
		final int value = sumInt(StatusBasedValueModifier::getCastDurationValue);

		return wrapped.getDodge() * mod + value;
	}

	@Override
	public int getHitrate() {
		
		final float mod = sumFloat(StatusBasedValueModifier::getHitrateMod);
		final int value = sumInt(StatusBasedValueModifier::getHitrateValue);

		return Math.round(wrapped.getDodge() * mod) + value;
	}

	@Override
	public int getMinDamage() {
		
		final float mod = sumFloat(StatusBasedValueModifier::getMinDamageMod);
		final int value = sumInt(StatusBasedValueModifier::getMinDamageValue);

		return Math.round(wrapped.getDodge() * mod) + value;
	}

	@Override
	public int getRangedBonusDamage() {
		
		final float mod = sumFloat(StatusBasedValueModifier::getRangedBonusDamageMod);
		final int value = sumInt(StatusBasedValueModifier::getRangedBonusDamageValue);

		return Math.round(wrapped.getDodge() * mod) + value;
	}

	@Override
	public float getAttackSpeed() {
		
		final float mod = sumFloat(StatusBasedValueModifier::getAttackSpeedMod);
		final int value = sumInt(StatusBasedValueModifier::getAttackSpeedValue);

		return Math.round(wrapped.getDodge() * mod) + value;
	}

	@Override
	public Walkspeed getWalkspeed() {
		
		final float mod = sumFloat(StatusBasedValueModifier::getWalkspeedMod);
		final int value = sumInt(StatusBasedValueModifier::getWalkspeedValue);
		int walkspeed = Math.round(wrapped.getDodge() * mod) + value;

		return Walkspeed.Companion.fromInt(walkspeed);
	}

	@Override
	public float getCooldownMod() {
		return wrapped.getCooldownMod();
	}

}
