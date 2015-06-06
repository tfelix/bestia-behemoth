package net.bestia.model;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration(locations = {"/spring-config.xml"})
public abstract class DomainAwareBase extends AbstractJUnit4SpringContextTests {
	
	private final String deleteScript = "src/main/resources/sql/cleanup.sql";
	
	@Autowired
	private DataSource datasource;
	
	@Before
    public void deleteAllDomainEntities() throws ScriptException, SQLException {
        ScriptUtils.executeSqlScript(datasource.getConnection(), new FileSystemResource(deleteScript));
    }

}
