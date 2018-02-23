package net.entity.component;

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
		
		this.exp = exp;
		this.level = level;
	}

	@Override
	protected void performSetting(LevelComponent comp) {

		comp.setLevel(level);
		comp.setExp(exp);
	}

}
