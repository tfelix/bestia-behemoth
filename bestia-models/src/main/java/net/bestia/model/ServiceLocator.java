package net.bestia.model;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Central class to access DAOs which might heavily depend upon Springs autowiring. So in order to get properly wired
 * objects use this class.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ServiceLocator {

	private static ServiceLocator instance = new ServiceLocator();

	private final ApplicationContext context = new ClassPathXmlApplicationContext("/spring-config.xml");

	/**
	 * Private Ctor for singelton.
	 */
	private ServiceLocator() {

	}

	/**
	 * Returns an instance of this class.
	 * 
	 * @return An instance of this class.
	 */
	public static ServiceLocator getInstance() {
		return instance;
	}

	/**
	 * Creates a spring injected bean.
	 * 
	 * @param type
	 *            BeanType which the user wants to receive with injected objects via spring.
	 * @return The created bean of the given type.
	 */
	public <T> T getBean(Class<T> type) {
		return context.getBean(type);
	}

}
