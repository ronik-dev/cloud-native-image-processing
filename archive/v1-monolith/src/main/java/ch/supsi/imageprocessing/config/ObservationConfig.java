package ch.supsi.imageprocessing.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.observation.ObservationTextPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationConfig {

		private static final Logger log = LoggerFactory.getLogger(ObservationConfig.class);

		@Bean
		public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
				return new ObservedAspect(observationRegistry);
		}

		@Bean
		public ObservationTextPublisher observationTextPublisher() {
				return new ObservationTextPublisher(log::info); 
		}
}
