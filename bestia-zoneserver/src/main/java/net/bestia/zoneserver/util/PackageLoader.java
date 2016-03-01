package net.bestia.zoneserver.util;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;

/**
 * This class can load instances of sub types of a given base-type. Because of
 * type erasure using this class is a bit cumbersome. Instance it with the type
 * of the base class as well as the variant of the base class. To search for the
 * classes then call the appropriate method.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PackageLoader<BaseT> {

	private static final Logger LOG = LogManager.getLogger(PackageLoader.class);

	private final String packageName;
	private boolean hasLoaded = false;
	private final Class<BaseT> typeParameterClass;

	private final Set<Class<? extends BaseT>> subclasses = new HashSet<>();

	public PackageLoader(Class<BaseT> type, String packageName) {
		this.packageName = packageName;
		this.typeParameterClass = type;
	}

	/**
	 * Returns a Set of all the subclasses for the given super-type and package.
	 * 
	 * @return The set of subclasses.
	 */
	public Set<Class<? extends BaseT>> getSubClasses() {

		if (!hasLoaded) {
			load();
		}

		return subclasses;
	}

	private void load() {
		final Reflections reflections = new Reflections(packageName);
		subclasses.addAll(reflections.getSubTypesOf(typeParameterClass));
	}

	/**
	 * Tries to instantiate the given sub-types of objects if this is needed. Of
	 * course the objects need a std. ctor in order for this to work.
	 * 
	 * @return The Set of instantiated objects.
	 */
	public Set<BaseT> getSubObjects() {
		if (!hasLoaded) {
			load();
		}

		final Set<BaseT> objInstances = new HashSet<>();
		for (Class<? extends BaseT> clazz : subclasses) {
			// Dont instance abstract classes.
			if (Modifier.isAbstract(clazz.getModifiers())) {
				LOG.trace("Can not instanciate (is Abstract) : {}", clazz.toString());
				continue;
			}

			try {
				final BaseT extra = clazz.newInstance();
				objInstances.add(extra);
			} catch (InstantiationException | IllegalAccessException e) {
				LOG.error("Can not instanciate (has no std. ctor.): {}", clazz.toString(), e);
			}
		}

		return objInstances;
	}

	/**
	 * Returns true if all referenced subclasses have at least a standard ctor.
	 * false if otherwise. Useful for fast unit testing if all classes obtain a
	 * std. ctor and fail the test otherwise.
	 * 
	 * @return TRUE if all referenced subclasses have at least a std. ctor.
	 *         FALSE otherwise.
	 */
	public boolean haveAllStdCtor() {
		if (!hasLoaded) {
			load();
		}

		for (Class<? extends BaseT> clazz : subclasses) {
			// Dont instance abstract classes.
			if (Modifier.isAbstract(clazz.getModifiers())) {
				continue;
			}

			try {
				clazz.getConstructor();
			} catch (NoSuchMethodException | SecurityException e) {
				LOG.warn("Class {} has no accessable std. ctor.", clazz.getSimpleName(), e);
				return false;
			}

		}

		return true;
	}

}
