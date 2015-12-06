package net.bestia.model;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration(locations = {"/spring-config.xml"})
public abstract class DomainAwareBase extends AbstractJUnit4SpringContextTests {
	
	//private final String deleteScript = "src/test/resources/sql/cleanup.sql";
	//private final String createScript = "src/test/resources/sql/create-data.sql";
	
	@Autowired
	private DataSource datasource;
	
	@Before
    public void deleteAllDomainEntities() throws ScriptException, SQLException {
		//ScriptUtils.executeSqlScript(datasource.getConnection(), new FileSystemResource(createScript));
    }

}
