package net.bestia.model.battle;

import java.util.List;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import net.bestia.model.domain.StatusPoints;

/**
 * This implements the {@link StatusPoints} interface but this class allows to
 * add {@link StatusPointsModifier} which will modify the status upon returning.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class StatusPointsDecorator implements StatusPoints, Serializable {

	private static final long serialVersionUID = 1L;

	private final StatusPoints wrapped;

	private List<StatusPointsModifier> statusMods = new ArrayList<>();

	/**
	 * Ctor. Wrapps the given {@link StatusPoints} object and allows the
	 * modification of the underlying status points via adding of
	 * {@link StatusPointsModifier}.
	 * 
	 * @param wrapped
	 *            The wrapped {@link StatusPoints} object.
	 */
	public StatusPointsDecorator(StatusPoints wrapped) {

		this.wrapped = Objects.requireNonNull(wrapped);
	}

	public void addModifier(StatusPointsModifier mod) {
		statusMods.add(Objects.requireNonNull(mod));
	}

	public void removeModifier(StatusPointsModifier mod) {
		statusMods.remove(mod);
	}

	private float sumFloat(ToDoubleFunction<? super StatusPointsModifier> func) {
		return (float) statusMods.stream()
				.mapToDouble(func)
				.sum();
	}

	private int sumInt(ToIntFunction<? super StatusPointsModifier> func) {
		return statusMods.stream()
				.mapToInt(func)
				.sum();
	}

	@Override
	public int getCurrentHp() {
		return wrapped.getCurrentHp();
	}

	@Override
	public int getMaxHp() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getCurrentMana() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxMana() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getDefense() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMagicDefense() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getStrength() {

		final int strengthVal = sumInt(StatusPointsModifier::getStrengthValue);
		final float stengthMod = sumFloat(StatusPointsModifier::getStrengthMod);

		return Math.round(wrapped.getStrength() * stengthMod) + strengthVal;
	}

	@Override
	public int getVitality() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getIntelligence() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getAgility() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getWillpower() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getDexterity() {
		// TODO Auto-generated method stub
		return 0;
	}

	public void setDexterity(int dexterity) {
		wrapped.setDexterity(dexterity);
	}

	public void setWillpower(int willpower) {
		wrapped.setWillpower(willpower);
	}

	public void setAgility(int agi) {
		wrapped.setAgility(agi);
	}

	public void setIntelligence(int intel) {
		wrapped.setIntelligence(intel);
	}

	public void setVitality(int vit) {
		wrapped.setVitality(vit);
	}

	public void setStrenght(int str) {
		wrapped.setStrenght(str);
	}

	public void setMagicDefense(int mdef) {
		wrapped.setMagicDefense(mdef);
	}

	public void setDefense(int def) {
		wrapped.setDefense(def);
	}

	public void setMaxMana(int maxMana) {
		wrapped.setMaxMana(maxMana);
	}

	public void setCurrentMana(int mana) {
		wrapped.setCurrentMana(mana);
	}

	public void setMaxHp(int maxHp) {
		wrapped.setMaxHp(maxHp);
	}

	public void setCurrentHp(int hp) {
		wrapped.setCurrentHp(hp);
	}

	public void clearModifier() {
		statusMods.clear();
	}

}
