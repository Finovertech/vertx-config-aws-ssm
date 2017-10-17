package com.finovertech.config.aws;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;

import io.vertx.config.spi.ConfigStore;
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

    private final String path;
    private final boolean decrypt;
    private final boolean recursive;
    private final Vertx vertx;
    private final AWSSimpleSystemsManagementClient ssmClient;

    /**
     * creates an instance of {@link AwsSsmConfigStore}.
     *
     * @param vertx
     *            the vert.x instance
     * @param config
     *            the configuration, used for creating the SSM client & requests.
     */
    public AwsSsmConfigStore(final Vertx vertx, final JsonObject config) {
        this.vertx = vertx;
        this.path = Objects.requireNonNull(config.getString("path"), "The path must be set.");
        this.decrypt = config.getBoolean("decrypt", true);
        this.recursive = config.getBoolean("recursive", true);
        ssmClient = (AWSSimpleSystemsManagementClient) AWSSimpleSystemsManagementClientBuilder.defaultClient();
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
                    final Map<String, Object> map = getParamsFromAwsAsMap();
                    future.complete(new JsonObject(map));
                } catch (final Throwable thr) {
                    future.fail(thr);
                }
            },
            result.completer());
        return result;
    }

    private Map<String, Object> getParamsFromAwsAsMap() {
        final Map returnMap = new HashMap<String, Object>();

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

    private void addParamsToMap(final Map returnMap, final GetParametersByPathResult response) {
        response.getParameters()
            .forEach(p -> returnMap.put(p.getName(), p.getValue()));
    }

    private GetParametersByPathResult makeAwsRequest(final String nextToken) {
        GetParametersByPathRequest request = new GetParametersByPathRequest()
            .withPath(path)
            .withWithDecryption(decrypt)
            .withRecursive(recursive);
        if (nextToken != null) {
            request = request.withNextToken(nextToken);
        }
        return ssmClient.getParametersByPath(request);
    }

    private Future<Buffer> toBuffer(final JsonObject json) {
        final Future<Buffer> future = Future.future();

        if (json == null) {
            future.complete(new JsonObject().toBuffer());
        } else {
            future.complete(json.toBuffer());
        }

        return future;
    }

    @Override
    public void close(final Handler<Void> completionHandler) {
        ssmClient.shutdown();
        completionHandler.handle(null);
    }
}
