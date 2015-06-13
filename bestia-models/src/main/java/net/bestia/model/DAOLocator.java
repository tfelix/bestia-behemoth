package net.bestia.model;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Central class to access DAOs which might heavily depend upon Springs autowiring. So in order to
 * get properly wired objects use this class.
 * 
 * @author Thomas
 *
 */
public class DAOLocator {

	private static final ApplicationContext context = new ClassPathXmlApplicationContext("/spring-config.xml");
	
	public <T> T getDAO(Class<T> type) {
		return context.getBean(type);
	}
	
}
