package ai.pipestream.module.parser;

import ai.pipestream.data.module.PipeStepProcessor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ParserServiceTest extends ParserServiceTestBase {

    @GrpcClient
    PipeStepProcessor pipeStepProcessor;

    @Override
    protected PipeStepProcessor getParserService() {
        return pipeStepProcessor;
    }
}