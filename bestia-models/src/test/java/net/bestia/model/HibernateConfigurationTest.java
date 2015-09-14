package net.bestia.model;


import static org.junit.Assert.*;

import org.hibernate.SessionFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

//@ContextConfiguration(locations = "/spring-config.xml")
// extends AbstractJUnit4SpringContextTests
public class HibernateConfigurationTest {
	
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
