package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.notification.NotificationModels.PushNotificationMessage;
import org.example.application.port.out.GeneratorNotificationTokenPort;
import org.example.application.port.out.PushNotificationPort;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.GeneratorNotificationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Singleton
public class CollectionStatusNotificationUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionStatusNotificationUseCase.class);

    @Inject
    GeneratorNotificationTokenPort tokenPort;

    @Inject
    PushNotificationPort pushNotificationPort;

    public Uni<Void> notifyGenerator(CollectionRequest request, String eventType) {
        if (request == null || request.getGeneratorId() == null || request.getGeneratorId().isBlank()) {
            return Uni.createFrom().voidItem();
        }

        LOG.info("Resolving generator notification tokens requestId={} generatorId={} eventType={}",
                request.getId(), request.getGeneratorId(), eventType);

        return tokenPort.findEnabledByGeneratorId(request.getGeneratorId())
                .flatMap(tokens -> sendToTokens(request, eventType, tokens))
                .onFailure().invoke(ex -> LOG.warn("Failed to publish generator notification requestId={} eventType={}",
                        request.getId(), eventType, ex))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    private Uni<Void> sendToTokens(CollectionRequest request, String eventType, List<GeneratorNotificationToken> tokens) {
        List<String> values = tokens == null ? List.of() : tokens.stream()
                                                           .filter(GeneratorNotificationToken::isEnabled)
                                                           .map(GeneratorNotificationToken::getToken)
                                                           .filter(token -> token != null && !token.isBlank())
                                                           .distinct()
                                                           .toList();

        if (values.isEmpty()) {
            LOG.info("No enabled notification tokens found requestId={} generatorId={} eventType={}",
                    request.getId(), request.getGeneratorId(), eventType);
            return Uni.createFrom().voidItem();
        }

        LOG.info("Sending generator notification requestId={} generatorId={} eventType={} tokenCount={}",
                request.getId(), request.getGeneratorId(), eventType, values.size());

        PushNotificationMessage message = new PushNotificationMessage(
                request.getGeneratorId(),
                values,
                titleFor(eventType),
                bodyFor(eventType),
                Map.of(
                        "eventType", nullToEmpty(eventType),
                        "requestId", nullToEmpty(request.getId()),
                        "generatorId", nullToEmpty(request.getGeneratorId()),
                        "collectorId", nullToEmpty(request.getSelectedCollectorId()),
                        "status", request.getStatus() == null ? "" : request.getStatus().toString()
                )
        );
        return pushNotificationPort.send(message);
    }

    private String titleFor(String eventType) {
        return switch (eventType) {
            case "COLLECTION_ACCEPTED" -> "Coleta aceita";
            case "COLLECTION_ON_THE_WAY" -> "Coletor a caminho";
            case "COLLECTION_COMPLETED" -> "Coleta concluida";
            case "COLLECTION_CANCELLED" -> "Coleta cancelada";
            case "COLLECTION_REJECTED" -> "Coleta recusada";
            default -> "Atualizacao da coleta";
        };
    }

    private String bodyFor(String eventType) {
        return switch (eventType) {
            case "COLLECTION_ACCEPTED" -> "Um coletor aceitou sua solicitacao.";
            case "COLLECTION_ON_THE_WAY" -> "O coletor esta a caminho do endereco da coleta.";
            case "COLLECTION_COMPLETED" -> "Sua coleta foi marcada como concluida.";
            case "COLLECTION_CANCELLED" -> "Sua coleta foi cancelada.";
            case "COLLECTION_REJECTED" -> "O coletor recusou a solicitacao.";
            default -> "Sua coleta foi atualizada.";
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
