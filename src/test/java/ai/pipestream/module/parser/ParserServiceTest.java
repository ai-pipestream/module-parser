package ai.pipestream.module.parser;

import ai.pipestream.data.module.v1.PipeStepProcessorService;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ParserServiceTest extends ParserServiceTestBase {

    @GrpcClient
    PipeStepProcessorService pipeStepProcessor;

    @Override
    protected PipeStepProcessorService getParserService() {
        return pipeStepProcessor;
    }
}
