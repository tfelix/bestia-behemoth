package net.bestia.zoneserver.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;

@Configuration
public class HazelcastConfiguration {
	
	@Bean
	public Config config() {
		Config cfg = new ClasspathXmlConfig("hazelcast.xml");
		cfg.setClassLoader(HazelcastConfiguration.class.getClassLoader());
		
		return cfg;
	}

}
