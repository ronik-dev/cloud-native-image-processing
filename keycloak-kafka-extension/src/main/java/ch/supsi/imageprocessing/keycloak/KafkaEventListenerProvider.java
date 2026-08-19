package ch.supsi.imageprocessing.keycloak;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;

public class KafkaEventListenerProvider implements EventListenerProvider {

		private final KeycloakSession session;
		private final KafkaProducer<String, String> producer;
		private final String topic;

		public KafkaEventListenerProvider(KeycloakSession session, KafkaProducer<String, String> producer, String topic) {
				this.session = session;
				this.producer = producer;
				this.topic = topic;
		}

		@Override
		public void onEvent(Event event) {
				// Handle User-Initiated Account Deletion
				if (event.getType() == EventType.DELETE_ACCOUNT) {
						String username = event.getDetails() != null ? event.getDetails().get("username") : null;
						if (username != null) {
								publishDeleteEvent(username);
						}
				}
		}

		@Override
		public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
				// Handle Admin-Initiated User Deletion
				if (adminEvent.getResourceType() == ResourceType.USER && adminEvent.getOperationType() == OperationType.DELETE) {

						// Keycloak Admin events provide the resource path like "users/<uuid>"
						String resourcePath = adminEvent.getResourcePath();
						if (resourcePath != null && resourcePath.startsWith("users/")) {
								String userId = resourcePath.substring(6); // Extract UUID

								// We must look up the user in the session before the transaction fully commits to get the username
								UserModel user = session.users().getUserById(session.getContext().getRealm(), userId);
								if (user != null && user.getUsername() != null) {
										publishDeleteEvent(user.getUsername());
								}
						}
				}
		}

		private void publishDeleteEvent(String username) {
				// Create a simple JSON string to match the UserEventMessage record
				String payload = String.format("{\"username\": \"%s\", \"action\": \"DELETE\"}", username);

				ProducerRecord<String, String> record = new ProducerRecord<>(topic, username, payload);
				producer.send(record);
		}

		@Override
		public void close() { }
}
