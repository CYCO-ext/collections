package org.example.application.notification;

import java.util.List;
import java.util.Map;

public final class NotificationModels {
    private NotificationModels() {
    }

    public record PushNotificationMessage(
            String recipientUserId,
            List<String> tokens,
            String title,
            String body,
            Map<String, String> data
    ) {
        public PushNotificationMessage {
            tokens = tokens == null ? List.of() : List.copyOf(tokens);
            data = data == null ? Map.of() : Map.copyOf(data);
        }
    }
}
