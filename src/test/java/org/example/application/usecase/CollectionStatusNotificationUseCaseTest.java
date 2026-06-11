package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.notification.NotificationModels.PushNotificationMessage;
import org.example.application.port.out.GeneratorNotificationTokenPort;
import org.example.application.port.out.PushNotificationPort;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.GeneratorNotificationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionStatusNotificationUseCaseTest {

    private CollectionStatusNotificationUseCase useCase;
    private GeneratorNotificationTokenPort tokenPort;
    private PushNotificationPort pushNotificationPort;

    @BeforeEach
    void setUp() {
        tokenPort = mock(GeneratorNotificationTokenPort.class);
        pushNotificationPort = mock(PushNotificationPort.class);
        useCase = new CollectionStatusNotificationUseCase();
        useCase.tokenPort = tokenPort;
        useCase.pushNotificationPort = pushNotificationPort;
    }

    @Test
    void sendsNotificationToEnabledTokens() {
        CollectionRequest request = request();
        when(tokenPort.findEnabledByGeneratorId("generator-1")).thenReturn(Uni.createFrom().item(List.of(
                token("token-1"),
                token("token-1"),
                token("token-2")
        )));
        when(pushNotificationPort.send(org.mockito.ArgumentMatchers.any())).thenReturn(Uni.createFrom().voidItem());

        useCase.notifyGenerator(request, "COLLECTION_ON_THE_WAY").await().indefinitely();

        ArgumentCaptor<PushNotificationMessage> captor = ArgumentCaptor.forClass(PushNotificationMessage.class);
        verify(pushNotificationPort).send(captor.capture());
        PushNotificationMessage message = captor.getValue();
        assertEquals("generator-1", message.recipientUserId());
        assertEquals(List.of("token-1", "token-2"), message.tokens());
        assertEquals("COLLECTION_ON_THE_WAY", message.data().get("eventType"));
        assertEquals("ON_THE_WAY", message.data().get("status"));
    }

    @Test
    void skipsPushWhenGeneratorHasNoTokens() {
        CollectionRequest request = request();
        when(tokenPort.findEnabledByGeneratorId("generator-1")).thenReturn(Uni.createFrom().item(List.of()));

        useCase.notifyGenerator(request, "COLLECTION_COMPLETED").await().indefinitely();

        verify(pushNotificationPort, never()).send(org.mockito.ArgumentMatchers.any());
    }

    private CollectionRequest request() {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId("request-1");
        request.setSelectedCollectorId("collector-1");
        request.setStatus(CollectionRequest.Status.ON_THE_WAY);
        return request;
    }

    private GeneratorNotificationToken token(String value) {
        return new GeneratorNotificationToken("generator-1", value, "ANDROID");
    }
}
