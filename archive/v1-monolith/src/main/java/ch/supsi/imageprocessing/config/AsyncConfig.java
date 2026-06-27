package ch.supsi.imageprocessing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

		private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

		@Override
		public Executor getAsyncExecutor() {
				ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
				executor.setCorePoolSize(2);
				executor.setMaxPoolSize(10);
				executor.setQueueCapacity(500);
				executor.setThreadNamePrefix("ImageProcessor-");

				// Propagates trace ID and other context to the async thread
				executor.setTaskDecorator(new ContextPropagatingTaskDecorator());

				executor.initialize();
				return executor;
		}

		@Override
		public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
				return (throwable, method, params) ->
						log.error("Uncaught exception in async method '{}' with params {}: {}",
										method.getName(), Arrays.toString(params), throwable.getMessage(), throwable);
		}
}
