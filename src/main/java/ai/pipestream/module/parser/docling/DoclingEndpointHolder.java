package ai.pipestream.module.parser.docling;

import ai.docling.serve.api.DoclingServeApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the active Docling endpoint URL and client. Supports live swapping
 * with automatic rollback if the new endpoint fails a health probe.
 */
@ApplicationScoped
public class DoclingEndpointHolder {

    private static final Logger LOG = Logger.getLogger(DoclingEndpointHolder.class);

    @Inject
    DoclingServeApi initialClient;

    private final AtomicReference<String> activeUrl = new AtomicReference<>();
    private final AtomicReference<DoclingServeApi> activeClient = new AtomicReference<>();

    /**
     * Returns the active Docling client. On first call, returns the injected client.
     */
    public DoclingServeApi getClient() {
        DoclingServeApi client = activeClient.get();
        return client != null ? client : initialClient;
    }

    /**
     * Returns the active endpoint URL, or "default" if using the injected client.
     */
    public String getActiveUrl() {
        String url = activeUrl.get();
        return url != null ? url : "default";
    }

    /**
     * Swaps the Docling endpoint to a new URL. Probes the new endpoint first.
     * If the probe fails, rolls back to the previous endpoint.
     *
     * @param newUrl the new Docling base URL
     * @return result of the swap attempt
     */
    public SwapResult swap(String newUrl) {
        String previousUrl = getActiveUrl();
        DoclingServeApi previousClient = getClient();

        LOG.infof("Attempting Docling endpoint swap: %s -> %s", previousUrl, newUrl);

        try {
            // Build a new client pointing to the new URL
            DoclingServeApi newClient = previousClient.toBuilder()
                    .baseUrl(newUrl)
                    .build();

            // Health probe — call the health endpoint
            newClient.health();

            // Probe passed — activate the new client
            activeClient.set(newClient);
            activeUrl.set(newUrl);
            LOG.infof("Docling endpoint swap successful: now using %s", newUrl);
            return new SwapResult(true, newUrl, previousUrl, null);

        } catch (Exception e) {
            LOG.errorf(e, "Docling endpoint swap failed for %s — rolling back to %s", newUrl, previousUrl);
            // No state change needed — activeClient/activeUrl still point to previous
            return new SwapResult(false, previousUrl, previousUrl, e.getMessage());
        }
    }

    public record SwapResult(boolean success, String activeUrl, String previousUrl, String error) {}
}
