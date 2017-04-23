package net.bestia.zoneserver.entity.components;

import java.util.Objects;

public abstract class ComponentSetter<T extends Component> {
	
	private final Class<T> type;
	
	public ComponentSetter(Class<T> type) {
		this.type = Objects.requireNonNull(type);
	}
	
	public Class<T> getSupportedType() {
		return type;
	}
	
	public abstract void setComponent(Component addedComp);	
}
