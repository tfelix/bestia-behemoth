package net.entity.factory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.bestia.entity.component.Component;

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

		private Set<Class<? extends Component>> components = new HashSet<>();

		/**
		 * Adds a new component to the builder.
		 * 
		 * @param clazz
		 *            A new component.
		 * @return The builder itself.
		 */
		public Builder addComponent(Class<? extends Component> clazz) {
			components.add(clazz);
			return this;
		}

		/**
		 * Cleans up the whole builder so it can be reused.
		 */
		public void clear() {
			components.clear();
		}

		/**
		 * Creates a new blueprint object.
		 * 
		 * @return The blueprint object.
		 */
		public Blueprint build() {
			return new Blueprint(this);
		}
	}

	private List<Class<? extends Component>> components;

	/**
	 * Ctor with builder pattern.
	 * 
	 * @param builder
	 *            The builder.
	 */
	private Blueprint(Builder builder) {

		this.components = new ArrayList<>(Objects.requireNonNull(builder).components);
	}

	/**
	 * Returns all included components.
	 * 
	 * @return
	 */
	public Collection<Class<? extends Component>> getComponents() {
		return Collections.unmodifiableList(components);
	}

	@Override
	public String toString() {
		return String.format("Blueprint%s",
				components.stream().map(x -> x.getSimpleName()).collect(Collectors.toList()));
	}
}
