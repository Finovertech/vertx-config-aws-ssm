package com.finovertech.vertx.config.aws.ssm;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;

import io.vertx.config.spi.ConfigStore;
import io.vertx.config.spi.utils.JsonObjectHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * An implementation of {@link ConfigStore} for AWS SSM Parameter Store.
 * (https://aws.amazon.com/ec2/systems-manager/parameter-store/)
 */
public class AwsSsmConfigStore implements ConfigStore {

    private final Function<String, GetParametersByPathRequest> requestBuilder;
    private final Vertx vertx;
    private final AWSSimpleSystemsManagement ssmClient;
    private final Function<Map<String, Object>, Map<String, Object>> entryMapper;

    /**
     * creates an instance of {@link AwsSsmConfigStore}.
     *
     * @param vertx
     *            the vert.x instance
     * @param builder
     *            A request builder function to invoke
     * @param client
     *            An AWS SSM Client instance to use.
     */
    public AwsSsmConfigStore(final Vertx vertx, final AWSSimpleSystemsManagement client,
        final Function<String, GetParametersByPathRequest> builder,
        final Function<Map<String, Object>, Map<String, Object>> entryMapper) {
        this.vertx = Objects.requireNonNull(vertx, "Vert.x instance required");
        this.requestBuilder = Objects.requireNonNull(builder, "Request builder method required.");
        this.ssmClient = Objects.requireNonNull(client, "SSM Client required");
        this.entryMapper = Objects.requireNonNull(entryMapper, "Key mapper is required");
    }

    @Override
    public void get(final Handler<AsyncResult<Buffer>> completionHandler) {
        getParameters() // Retrieve the data from AWS
            .compose(this::toBuffer) // Change to a Buffer
            .setHandler(completionHandler); // Done
    }

    private Future<JsonObject> getParameters() {
        final Future<JsonObject> result = Future.future();
        vertx.executeBlocking(
            future -> {
                try {
                    final Map<String, Object> map = entryMapper.apply(getParamsFromAwsAsMap());
                    future.complete(new JsonObject(map));
                } catch (final Throwable thr) {
                    future.fail(thr);
                }
            },
            result.completer());
        return result;
    }

    private Map<String, Object> getParamsFromAwsAsMap() {
        final Map<String, Object> returnMap = new HashMap<>();

        GetParametersByPathResult response = makeAwsRequest(null);
        addParamsToMap(returnMap, response);
        String nextToken = response.getNextToken();

        // call the AWS API until there is no longer a nextToken.
        while (nextToken != null) {
            response = makeAwsRequest(nextToken);
            addParamsToMap(returnMap, response);
            nextToken = response.getNextToken();
        }
        return returnMap;
    }

    private void addParamsToMap(final Map<String, Object> returnMap, final GetParametersByPathResult response) {
        response.getParameters()
            .forEach(p -> returnMap.put(p.getName(), p.getValue()));
    }

    private GetParametersByPathResult makeAwsRequest(final String nextToken) {
        final GetParametersByPathRequest request = Objects.requireNonNull(requestBuilder.apply(nextToken));
        return ssmClient.getParametersByPath(request);
    }

    private Future<Buffer> toBuffer(final JsonObject json) {
        final Future<Buffer> future = Future.future();

        if (json == null) {
            future.complete(Buffer.buffer());
        } else {
            future.complete(JsonObjectHelper.toBuffer(json));
        }

        return future;
    }

    @Override
    public void close(final Handler<Void> completionHandler) {
        ssmClient.shutdown();
        completionHandler.handle(null);
    }
}
