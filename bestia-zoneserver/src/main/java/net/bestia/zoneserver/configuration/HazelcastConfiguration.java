package net.bestia.zoneserver.configuration;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;

/**
 * Hazelcast specific configuration.
 *
 * @author Thomas Felix
 */
@Configuration
@Profile("!test")
public class HazelcastConfiguration {

  @Bean
  public HazelcastInstance getHazelcastClientInstance() throws IOException {
    ClientConfig clientConfig = new XmlClientConfigBuilder("hazelcast-client.xml").build();
    return HazelcastClient.newHazelcastClient(clientConfig);
  }
}
