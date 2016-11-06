package net.bestia.zoneserver.actor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.UntypedActor;
import net.bestia.zoneserver.util.PackageLoader;

public class GeneralActorTest {

	private final static Set<Class<? extends UntypedActor>> IGNORED_ACTORS = new HashSet<>();

	/**
	 * Tests if all actors have the correct spring component annotations.
	 */
	@Test
	public void correctSpringAnnotation() {
		PackageLoader<UntypedActor> actorLoader = new PackageLoader<>(UntypedActor.class,
				"net.bestia.zoneserver.actor");
		Set<Class<? extends UntypedActor>> classes = actorLoader.getSubClasses();

		for (Class<? extends UntypedActor> clazz : classes) {
			if (IGNORED_ACTORS.contains(clazz)) {
				continue;
			}

			Assert.assertTrue(clazz.isAnnotationPresent(Component.class));
			Assert.assertTrue(clazz.isAnnotationPresent(Scope.class));
			Scope scope = clazz.getAnnotation(Scope.class);
			Assert.assertEquals(scope.value(), "prototype");
		}
	}

	/**
	 * {@link BestiaRoutingActor} implementations should have a public static
	 * NAME field.
	 */
	@Test
	public void staticNameFieldPresen() {
		PackageLoader<BestiaRoutingActor> actorLoader = new PackageLoader<>(BestiaRoutingActor.class,
				"net.bestia.zoneserver.actor");
		Set<Class<? extends BestiaRoutingActor>> classes = actorLoader.getSubClasses();

		List<String> failedClasses = new ArrayList<>();
		for (Class<? extends UntypedActor> clazz : classes) {
			try {
				clazz.getField("NAME");
			} catch (Exception e) {
				failedClasses.add(clazz.getCanonicalName());
			}
		}

		if (failedClasses.size() > 0) {
			Assert.fail(
					"These classes do not implement a public static String NAME field: " + failedClasses.toString());
		}
	}
}
