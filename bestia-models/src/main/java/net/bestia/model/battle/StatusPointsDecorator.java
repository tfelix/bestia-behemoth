package net.bestia.model.battle;

import java.util.List;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;

import net.bestia.model.domain.StatusPoints;

public class StatusPointsDecorator implements StatusPoints, Serializable {

	private static final long serialVersionUID = 1L;

	private final StatusPoints wrapped;

	private List<StatusPointsModifier> statusMods = new ArrayList<>();

	public StatusPointsDecorator(StatusPoints wrapped) {

		this.wrapped = Objects.requireNonNull(wrapped);
	}

	public void addStatusModifier(StatusPointsModifier mod) {
		statusMods.add(Objects.requireNonNull(mod));
	}

	public void removeStatusModifier(StatusPointsModifier mod) {
		statusMods.remove(mod);
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
		
		final int strengthVal = statusMods.stream()
				.mapToInt(StatusPointsModifier::getStrengthValue)
				.sum();
		final float stengthMod = (float) statusMods.stream()
				.mapToDouble(StatusPointsModifier::getStrengthMod)
				.sum();
		
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
	}

	public void setWillpower(int willpower) {
	}

	public void setAgility(int agi) {
	}

	public void setIntelligence(int intel) {
	}

	public void setVitality(int vit) {
	}

	public void setStrenght(int str) {
	}

	public void setMagicDefense(int mdef) {
	}

	public void setDefense(int def) {
	}

	public void setMaxMana(int maxMana) {
	}

	public void setCurrentMana(int mana) {
	}

	public void setMaxHp(int maxHp) {
	}

	public void setCurrentHp(int hp) {
	}

}
