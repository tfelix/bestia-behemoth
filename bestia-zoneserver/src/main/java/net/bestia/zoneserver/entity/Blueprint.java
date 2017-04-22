package net.bestia.zoneserver.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.bestia.zoneserver.entity.components.Component;

/**
 * Blueprints are used to generate entities with a certain mix of components.
 * These components are then filled with data by the factories if certain
 * ComponentSetter are added to the factory. A blueprint itself is immutable
 * after it was created.
 * 
 * @author Thomas Felix
 *
 */
public class Blueprint {

	public static class Builder {

		private List<Class<? extends Component>> components = new ArrayList<>();

		public void addComponent(Class<? extends Component> clazz) {
			components.add(clazz);
		}

		public void clear() {
			components.clear();
		}

		public Blueprint build() {
			return new Blueprint(this);
		}
	}

	private List<Class<? extends Component>> components;

	private Blueprint(Builder builder) {

		this.components = new ArrayList<>(Objects.requireNonNull(builder).components);
	}

	/**
	 * Returns all included components.
	 * 
	 * @return
	 */
	Collection<Class<? extends Component>> getComponents() {
		return Collections.unmodifiableList(components);
	}

	@Override
	public String toString() {
		return String.format("Blueprint[%s]", components);
	}
}
