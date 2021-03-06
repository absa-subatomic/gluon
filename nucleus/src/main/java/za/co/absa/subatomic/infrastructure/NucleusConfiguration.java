package za.co.absa.subatomic.infrastructure;

import java.util.Collections;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import za.co.absa.subatomic.infrastructure.configuration.GluonProperties;

@Configuration
@EnableConfigurationProperties({ AtomistConfigurationProperties.class,
        GluonProperties.class })
public class NucleusConfiguration {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate(
                new BufferingClientHttpRequestFactory(
                        new SimpleClientHttpRequestFactory()));
        restTemplate.setInterceptors(
                Collections.singletonList(new RequestLoggingInterceptor()));

        return restTemplate;
    }
}
