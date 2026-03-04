package ai.pipestream.module.parser.service;

import ai.pipestream.data.v1.FileStorageReference;
import ai.pipestream.quarkus.dynamicgrpc.DynamicGrpcClientFactory;
import ai.pipestream.repository.pipedoc.v1.GetBlobRequest;
import ai.pipestream.repository.pipedoc.v1.GetBlobResponse;
import ai.pipestream.repository.pipedoc.v1.GetPipeDocRequest;
import ai.pipestream.repository.pipedoc.v1.GetPipeDocResponse;
import ai.pipestream.repository.pipedoc.v1.ListPipeDocsRequest;
import ai.pipestream.repository.pipedoc.v1.ListPipeDocsResponse;
import ai.pipestream.repository.pipedoc.v1.MutinyPipeDocServiceGrpc;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Thin client around repository-service PipeDoc gRPC APIs.
 */
@ApplicationScoped
public class RepositoryDocumentClient {

    private static final Logger LOG = Logger.getLogger(RepositoryDocumentClient.class);
    private static final String REPOSITORY_SERVICE_NAME = "repository";

    @Inject
    DynamicGrpcClientFactory grpcClientFactory;

    public Uni<ListPipeDocsResponse> listPipeDocs(String drive, int limit, String connectorId) {
        String resolvedDrive = (drive == null || drive.isBlank()) ? "default" : drive;
        int resolvedLimit = (limit <= 0) ? 50 : limit;

        ListPipeDocsRequest.Builder requestBuilder = ListPipeDocsRequest.newBuilder()
                .setDrive(resolvedDrive)
                .setLimit(resolvedLimit);

        if (connectorId != null && !connectorId.isBlank()) {
            requestBuilder.setConnectorId(connectorId);
        }

        return grpcClientFactory.getClient(REPOSITORY_SERVICE_NAME, MutinyPipeDocServiceGrpc::newMutinyStub)
                .flatMap(stub -> stub.listPipeDocs(requestBuilder.build()))
                .invoke(response -> LOG.debugf(
                        "Loaded %d repository docs (drive=%s)",
                        response.getPipedocsCount(),
                        resolvedDrive));
    }

    public Uni<GetPipeDocResponse> getPipeDoc(String nodeId) {
        return grpcClientFactory.getClient(REPOSITORY_SERVICE_NAME, MutinyPipeDocServiceGrpc::newMutinyStub)
                .flatMap(stub -> stub.getPipeDoc(GetPipeDocRequest.newBuilder().setNodeId(nodeId).build()));
    }

    public Uni<GetBlobResponse> getBlob(FileStorageReference storageRef) {
        return grpcClientFactory.getClient(REPOSITORY_SERVICE_NAME, MutinyPipeDocServiceGrpc::newMutinyStub)
                .flatMap(stub -> stub.getBlob(GetBlobRequest.newBuilder().setStorageRef(storageRef).build()));
    }
}
