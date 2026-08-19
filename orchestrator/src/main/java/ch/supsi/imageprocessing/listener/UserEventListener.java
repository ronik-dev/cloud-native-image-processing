package ch.supsi.imageprocessing.listener;

import ch.supsi.imageprocessing.common.dto.UserEventMessage;
import ch.supsi.imageprocessing.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);
    private final UserService userService;

    public UserEventListener(UserService userService) {
        this.userService = userService;
    }

    @KafkaListener(topics = "${kafka.topics.user-events}", groupId = "orchestrator-user-events")
    @Transactional
    public void onUserEvent(UserEventMessage message) {
        if ("DELETE".equalsIgnoreCase(message.action())) {
            log.info("Received DELETE event for Keycloak user: {}", message.username());
            userService.deleteByUsername(message.username());
        }
    }
}
