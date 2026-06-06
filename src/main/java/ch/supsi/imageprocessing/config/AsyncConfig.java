package ch.supsi.imageprocessing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

		@Bean(name = "taskExecutor")
		public Executor taskExecutor() {
				ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
				executor.setCorePoolSize(2);
				executor.setMaxPoolSize(10);
				executor.setQueueCapacity(500);
				executor.setThreadNamePrefix("ImageProcessor-");

				// To passe the Trace ID to the new thread
				executor.setTaskDecorator(new ContextPropagatingTaskDecorator());

				executor.initialize();
				return executor;
		}
}
