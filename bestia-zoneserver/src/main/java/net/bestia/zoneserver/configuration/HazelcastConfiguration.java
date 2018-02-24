package net.bestia.zoneserver.configuration;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Hazelcast specific configuration.
 *
 * @author Thomas Felix
 */
@Configuration
public class HazelcastConfiguration {

  @Bean
  public HazelcastInstance getHazelcastClientInstance() throws IOException {
    ClientConfig clientConfig = new XmlClientConfigBuilder("hazelcast-client.xml").build();
    return HazelcastClient.newHazelcastClient(clientConfig);
  }
}
