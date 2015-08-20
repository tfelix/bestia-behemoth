package net.bestia.model;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Central class to access DAOs which might heavily depend upon Springs autowiring. So in order to
 * get properly wired objects use this class.
 * TODO den hier vielliecht als singelton umsetzen?
 * 
 * @author Thomas
 *
 */
public class ServiceLocator {

	private static final ApplicationContext context = new ClassPathXmlApplicationContext("/spring-config.xml");

	public <T> T  getBean(Class<T> type) {
		return context.getBean(type);
	}
	
}
