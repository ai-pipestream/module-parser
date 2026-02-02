package ai.pipestream.module.parser;

import ai.pipestream.data.module.v1.GetServiceRegistrationRequest;
import ai.pipestream.data.module.v1.GetServiceRegistrationResponse;
import ai.pipestream.data.module.v1.PipeStepProcessorServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusIntegrationTest
class ServiceRegistrationIT {

    private static ManagedChannel channel;
    private static PipeStepProcessorServiceGrpc.PipeStepProcessorServiceBlockingStub stub;

    @BeforeAll
    static void setupGrpcClient() {
        // Get the test port from Quarkus config
        int port = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.http.test-port", Integer.class)
                .orElse(8081);

        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();

        stub = PipeStepProcessorServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void shutdownGrpcClient() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    void testServiceRegistration() {
        GetServiceRegistrationRequest request = GetServiceRegistrationRequest.newBuilder().build();

        GetServiceRegistrationResponse registration = stub.getServiceRegistration(request);

        assertThat("Module name should be 'parser'", registration.getModuleName(), is(equalTo("parser")));
        assertThat("Registration should include JSON schema", registration.hasJsonConfigSchema(), is(true));
        assertThat("JSON schema should be valid", registration.getJsonConfigSchema().length(), is(greaterThan(100)));
        assertThat("Health check should pass", registration.getHealthCheckPassed(), is(true));
    }
}
