package org.example;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QuarkusMain
public class Main implements QuarkusApplication {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    @Override
    public int run(String... args) throws Exception {
        LOG.info("Waste Collection Microservice starting...");
        Quarkus.waitForExit();
        return 0;
    }

    public static void main(String[] args) {
        Quarkus.run(Main.class, args);
    }
}