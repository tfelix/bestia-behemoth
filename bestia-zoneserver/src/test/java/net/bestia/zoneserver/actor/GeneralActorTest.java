package net.bestia.zoneserver.actor;

import akka.actor.AbstractActor;
import net.bestia.util.PackageLoader;
import net.bestia.zoneserver.actor.connection.ClientConnectionActorEx;
import net.bestia.zoneserver.actor.entity.EntityActor;
import net.bestia.zoneserver.actor.zone.ClusterControlActor;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Especially testing if all actors are annotated correctly.
 * 
 * @author Thomas Felix
 *
 */
public class GeneralActorTest {

	private final static Set<Class<? extends AbstractActor>> IGNORED_ACTORS = new HashSet<>();
	
	static {
		IGNORED_ACTORS.add(ClusterControlActor.class);
		IGNORED_ACTORS.add(EntityActor.class);
		IGNORED_ACTORS.add(ClientConnectionActorEx.class);
	}

	/**
	 * Tests if all actors have the correct spring component annotations.
	 */
	@Test
	public void correctSpringAnnotation() {
		PackageLoader<AbstractActor> actorLoader = new PackageLoader<>(AbstractActor.class,
				"net.bestia.zoneserver.actor");
		Set<Class<? extends AbstractActor>> classes = actorLoader.getSubClasses();

		for (Class<? extends AbstractActor> clazz : classes) {

			// Ignore abstract classes.
			if (Modifier.isAbstract(clazz.getModifiers())) {
				continue;
			}

			if (IGNORED_ACTORS.contains(clazz)) {
				continue;
			}

			Assert.assertTrue("Missing component annotation for: " + clazz.getName(),
					clazz.isAnnotationPresent(Component.class));
			Assert.assertTrue(clazz.isAnnotationPresent(Scope.class));
			Scope scope = clazz.getAnnotation(Scope.class);
			Assert.assertEquals(scope.value(), "prototype");
		}
	}

	/**
	 * Implementations should have a public static
	 * NAME field.
	 */
	@Test
	public void staticNameFieldPresent() {
		PackageLoader<AbstractActor> actorLoader = new PackageLoader<>(AbstractActor.class,
				"net.bestia.zoneserver.actor");
		Set<Class<? extends AbstractActor>> classes = actorLoader.getConcreteSubClasses();

		List<String> failedClasses = new ArrayList<>();
		for (Class<? extends AbstractActor> clazz : classes) {
			try {
				clazz.getField("NAME");
			} catch (Exception e) {
				if(!IGNORED_ACTORS.contains(clazz)) {
					failedClasses.add(clazz.getCanonicalName());
				}
			}
		}

		if (failedClasses.size() > 0) {
			Assert.fail(
					"These classes do not implement a public static String NAME field: " + failedClasses.toString());
		}
	}
}
