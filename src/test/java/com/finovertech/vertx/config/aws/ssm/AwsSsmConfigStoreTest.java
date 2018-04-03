package com.finovertech.vertx.config.aws.ssm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;

import io.vertx.config.spi.ConfigStore;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class AwsSsmConfigStoreTest {

    @Rule
    public RunTestOnContext rule = new RunTestOnContext();

    @Test
    public void testPathParse(final TestContext context) {
        final AWSSimpleSystemsManagement ssmClient = mock(AWSSimpleSystemsManagement.class);
        when(ssmClient.getParametersByPath(any())).thenReturn(new GetParametersByPathResult()
            .withNextToken(null)
            .withParameters(new Parameter()
                .withName("/local/test/example")
                .withType("String")
                .withValue("value")));

        AwsSsmConfigStoreFactory.setClientSupplier(() -> ssmClient);

        final ConfigStore target = new AwsSsmConfigStoreFactory().create(rule.vertx(),
            new JsonObject()
                .put("path", "/local")
                .put("recursive", true)
                .put("decrypt", false)
                .put("parsePath", true));

        final Handler<AsyncResult<Buffer>> handler = context.asyncAssertSuccess(buffer -> {
            final JsonObject obj = buffer.toJsonObject();
            System.out.println(obj.encodePrettily());
            context.assertEquals("value", obj.getString("test/example"));
        });

        target.get(handler);
    }

    @Test
    public void testMultiPageFetch(final TestContext context) {
        final AWSSimpleSystemsManagement ssmClient = mock(AWSSimpleSystemsManagement.class);
        when(ssmClient.getParametersByPath(argThat(hasNoToken())))
            .thenReturn(new GetParametersByPathResult()
                .withNextToken("test")
                .withParameters(new Parameter()
                    .withName("/local/p1")
                    .withType("String")
                    .withValue("value")));

        when(ssmClient.getParametersByPath(argThat(hasToken("test"))))
            .thenReturn(new GetParametersByPathResult()
                .withNextToken(null)
                .withParameters(new Parameter()
                    .withName("/local/p2")
                    .withType("String")
                    .withValue("value")));

        AwsSsmConfigStoreFactory.setClientSupplier(() -> ssmClient);

        final ConfigStore target = new AwsSsmConfigStoreFactory().create(rule.vertx(),
            new JsonObject()
                .put("path", "/local")
                .put("recursive", true)
                .put("decrypt", false));

        final Handler<AsyncResult<Buffer>> handler = context.asyncAssertSuccess(buffer -> {
            final JsonObject obj = buffer.toJsonObject();
            System.out.println(obj.encodePrettily());
            context.assertEquals("value", obj.getString("/local/p1"));
            context.assertEquals("value", obj.getString("/local/p2"));

            verify(ssmClient, times(2)).getParametersByPath(any());
        });

        target.get(handler);
    }

    private ArgumentMatcher<GetParametersByPathRequest> hasToken(final String token) {
        return req -> req != null && token.equals(req.getNextToken());
    }

    private ArgumentMatcher<GetParametersByPathRequest> hasNoToken() {
        return req -> req != null && (req.getNextToken() == null || req.getNextToken().trim().length() == 0);
    }
}
