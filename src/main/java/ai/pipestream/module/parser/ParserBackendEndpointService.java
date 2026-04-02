package ai.pipestream.module.parser;

import ai.pipestream.data.module.v1.BackendEndpointService;
import ai.pipestream.data.module.v1.GetBackendEndpointsRequest;
import ai.pipestream.data.module.v1.GetBackendEndpointsResponse;
import ai.pipestream.data.module.v1.BackendEndpointInfo;
import ai.pipestream.data.module.v1.UpdateBackendEndpointRequest;
import ai.pipestream.data.module.v1.UpdateBackendEndpointResponse;
import ai.pipestream.module.parser.docling.DoclingEndpointHolder;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Implements BackendEndpointService for the parser module.
 * Supports live-swapping the Docling backend URL.
 */
@Singleton
@GrpcService
public class ParserBackendEndpointService implements BackendEndpointService {

    @Inject
    DoclingEndpointHolder doclingEndpointHolder;

    @Override
    public Uni<UpdateBackendEndpointResponse> updateBackendEndpoint(UpdateBackendEndpointRequest request) {
        String newUrl = request.getEndpointUrl();
        String backendId = request.getBackendId();

        if (!backendId.isEmpty() && !"docling".equals(backendId)) {
            return Uni.createFrom().item(UpdateBackendEndpointResponse.newBuilder()
                    .setSuccess(false)
                    .setActiveEndpointUrl(doclingEndpointHolder.getActiveUrl())
                    .setErrorMessage("Unknown backend_id: " + backendId + ". Supported: docling")
                    .build());
        }

        var result = doclingEndpointHolder.swap(newUrl);
        return Uni.createFrom().item(UpdateBackendEndpointResponse.newBuilder()
                .setSuccess(result.success())
                .setActiveEndpointUrl(result.activeUrl())
                .setPreviousEndpointUrl(result.previousUrl())
                .setErrorMessage(result.error() != null ? result.error() : "")
                .build());
    }

    @Override
    public Uni<GetBackendEndpointsResponse> getBackendEndpoints(GetBackendEndpointsRequest request) {
        return Uni.createFrom().item(GetBackendEndpointsResponse.newBuilder()
                .addEndpoints(BackendEndpointInfo.newBuilder()
                        .setBackendId("docling")
                        .setEndpointUrl(doclingEndpointHolder.getActiveUrl())
                        .setHealthy(doclingEndpointHolder.isHealthy())
                        .setDescription("Docling document analysis service")
                        .build())
                .build());
    }
}
