package net.bestia.model;


import static org.junit.Assert.*;

import org.hibernate.SessionFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration(locations = "/spring-config.xml")
public class HibernateConfigurationTest extends AbstractJUnit4SpringContextTests {
	
    private SessionFactory sessionFactory;
	
    public void testHibernateConfiguration() {
        // Spring IOC container instantiated and prepared sessionFactory
        assertNotNull (sessionFactory); 
    }
    
    @Test
    public void tempTest() {
    	Assert.assertTrue(true);
    }

}
