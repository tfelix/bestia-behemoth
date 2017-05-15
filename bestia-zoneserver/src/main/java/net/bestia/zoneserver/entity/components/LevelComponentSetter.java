package net.bestia.zoneserver.entity.components;

import net.bestia.zoneserver.entity.LevelService;

/**
 * Sets the value of a level component.
 * 
 * @author Thomas Felix
 *
 */
public class LevelComponentSetter extends ComponentSetter<LevelComponent> {

	private int level;
	private int exp;

	public LevelComponentSetter(int level, int exp) {
		super(LevelComponent.class);

		if (level < 0 || level > LevelService.MAX_LEVEL) {
			throw new IllegalArgumentException("Level can not be negative and bigger then" + LevelService.MAX_LEVEL);
		}

		if (exp < 0) {
			throw new IllegalArgumentException("Exp must be positive.");
		}

		this.exp = exp;
		this.level = level;
	}

	@Override
	protected void performSetting(LevelComponent comp) {

		comp.setLevel(level);
		comp.setExp(exp);
	}

}
