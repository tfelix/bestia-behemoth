package net.bestia.model.battle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import net.bestia.model.entity.IStatusBasedValues;

public class StatusBasedValuesDecorator implements IStatusBasedValues, Serializable {

	private static final long serialVersionUID = 1L;

	private final IStatusBasedValues wrapped;

	private List<StatusBasedValueModifier> statusMods = new ArrayList<>();

	public StatusBasedValuesDecorator(IStatusBasedValues wrapped) {

		this.wrapped = Objects.requireNonNull(wrapped);
	}

	public void addStatusModifier(StatusBasedValueModifier mod) {
		statusMods.add(Objects.requireNonNull(mod));
	}

	public void removeStatusModifier(StatusBasedValueModifier mod) {
		statusMods.remove(mod);
	}

	public void clearModifier() {
		statusMods.clear();
	}

	@Override
	public void setLevel(int level) {
		wrapped.setLevel(level);
	}
	
	private float sumFloat(ToDoubleFunction<? super StatusBasedValueModifier> func) {
		return (float) statusMods.stream()
				.mapToDouble(func)
				.sum();
	}
	
	private int sumInt(ToIntFunction<? super StatusBasedValueModifier> func) {
		return statusMods.stream()
				.mapToInt(func)
				.sum();
	}

	@Override
	public float getHpRegenRate() {
		
		final float hpRegenMod = sumFloat(StatusBasedValueModifier::getHpRegenMod);
		final int hpRegenValue = sumInt(StatusBasedValueModifier::getHpRegenValue);
		
		return wrapped.getHpRegenRate() * hpRegenMod + hpRegenValue;
	}

	@Override
	public float getManaRegenRate() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getCriticalHitrate() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getDodge() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getCasttime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getCastduration() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getWillpowerResistance() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getVitalityResistance() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getHitrate() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMinDamage() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getRangedBonusDamage() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getAttackSpeed() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getWalkspeed() {
		// TODO Auto-generated method stub
		return 0;
	}

}
