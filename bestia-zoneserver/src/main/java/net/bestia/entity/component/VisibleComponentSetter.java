package net.entity.component;

import java.util.Objects;

import bestia.model.domain.SpriteInfo;

public class VisibleComponentSetter extends ComponentSetter<VisibleComponent> {

	private SpriteInfo spriteInfo;

	public VisibleComponentSetter(SpriteInfo spriteInfo) {
		super(VisibleComponent.class);
		
		this.spriteInfo = Objects.requireNonNull(spriteInfo);
	}

	@Override
	protected void performSetting(VisibleComponent comp) {
		
		comp.setVisual(spriteInfo);
		
	}
}
