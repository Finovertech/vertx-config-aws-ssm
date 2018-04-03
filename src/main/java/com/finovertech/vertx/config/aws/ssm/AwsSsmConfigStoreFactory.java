package com.finovertech.vertx.config.aws.ssm;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.util.ImmutableMapParameter;

import io.vertx.config.spi.ConfigStore;
import io.vertx.config.spi.ConfigStoreFactory;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Implementation of {@link ConfigStoreFactory} to create {@link AwsSsmConfigStore}.
 */
public class AwsSsmConfigStoreFactory implements ConfigStoreFactory {

    private static Supplier<AWSSimpleSystemsManagement> clientSupplier = AWSSimpleSystemsManagementClientBuilder::defaultClient;

    @Override
    public String name() {
        return "aws-ssm";
    }

    @Override
    public ConfigStore create(final Vertx vertx, final JsonObject configuration) {
        return new AwsSsmConfigStore(vertx,
            clientSupplier.get(),
            requestBuilder(configuration),
            keyMapper(configuration));
    }

    private Function<Map<String, Object>, Map<String, Object>> keyMapper(final JsonObject configuration) {
        if (!configuration.getBoolean("parsePath", false)) {
            return Function.identity();
        }

        return ssmParamMap -> {
            final ImmutableMapParameter.Builder<String, Object> params = new ImmutableMapParameter.Builder<>();
            final int chop = getPathPrefix(configuration).length();

            ssmParamMap.forEach((key, value) -> params.put(key.substring(chop), value));

            return params.build();
        };
    }

    private Function<String, GetParametersByPathRequest> requestBuilder(final JsonObject configuration) {
        return nextToken -> {
            GetParametersByPathRequest request = new GetParametersByPathRequest()
                .withPath(getPathPrefix(configuration))
                .withWithDecryption(configuration.getBoolean("decrypt", true))
                .withRecursive(configuration.getBoolean("recursive", true));

            if (nextToken != null) {
                request = request.withNextToken(nextToken);
            }

            return request;
        };
    }

    private String getPathPrefix(final JsonObject configuration) {
        final String prefix = configuration.getString("path", "/");
        if (prefix.endsWith("/")) {
            return prefix;
        }

        return prefix + "/";
    }

    public static void setClientSupplier(final Supplier<AWSSimpleSystemsManagement> clientSupplier) {
        AwsSsmConfigStoreFactory.clientSupplier = clientSupplier;
    }
}
