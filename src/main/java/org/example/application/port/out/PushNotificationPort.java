package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.application.notification.NotificationModels.PushNotificationMessage;

public interface PushNotificationPort {
    Uni<Void> send(PushNotificationMessage message);
}
