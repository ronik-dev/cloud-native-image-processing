package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.common.dto.JobRequestMessage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.kafka.listener.auto-startup=false",
    "kafka.topics.user-events=user.events",
    "kafka.topics.job-requests=job.requests",
    "kafka.topics.job-results=job.results"
})
class CloudNativeImageProcessingApplicationTests {

    @MockitoBean
    private KafkaTemplate<String, JobRequestMessage> jobRequestKafkaTemplate;

    @Test
    void contextLoads() {
    }
}
