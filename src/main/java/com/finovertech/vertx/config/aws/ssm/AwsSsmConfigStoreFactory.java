package com.finovertech.vertx.config.aws.ssm;

import io.vertx.config.spi.ConfigStore;
import io.vertx.config.spi.ConfigStoreFactory;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Implementation of {@link ConfigStoreFactory} to create {@link AwsSsmConfigStore}.
 */
public class AwsSsmConfigStoreFactory implements ConfigStoreFactory {

    @Override
    public String name() {
        return "aws-ssm";
    }

    @Override
    public ConfigStore create(final Vertx vertx, final JsonObject configuration) {
        return new AwsSsmConfigStore(vertx, configuration);
    }
}
