package org.example.infrastructure.repository;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Map;

public class MongoTestResource implements QuarkusTestResourceLifecycleManager {
    private GenericContainer<?> mongo;

    @Override
    public Map<String, String> start() {
        mongo = new FixedMongoContainer().waitingFor(Wait.forListeningPort());
        mongo.start();
        return Map.of("quarkus.mongodb.connection-string", "mongodb://localhost:27017");
    }

    @Override
    public void stop() {
        if (mongo != null) mongo.stop();
    }

    private static final class FixedMongoContainer extends GenericContainer<FixedMongoContainer> {
        private FixedMongoContainer() {
            super(DockerImageName.parse("mongo:7.0"));
        }

        @Override
        protected void configure() {
            addFixedExposedPort(27017, 27017);
            super.configure();
        }
    }
}
