package ai.pipestream.module.parser.tika.builders;

import com.google.protobuf.Struct;
import ai.pipestream.parsed.data.epub.v1.EpubMetadata;
import ai.pipestream.parsed.data.tika.base.v1.TikaBaseFields;
import ai.pipestream.shaded.tika.metadata.Epub;
import ai.pipestream.shaded.tika.metadata.Metadata;
import ai.pipestream.shaded.tika.metadata.TikaCoreProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds EpubMetadata from Tika Metadata.
 * Minimal mapping for now (rendition_layout, version, mimetype),
 * with room to extend to spine/manifest/toc when available.
 */
public final class EpubMetadataBuilder {

    private EpubMetadataBuilder() {}

    public static EpubMetadata build(Metadata metadata, String parserClass, String tikaVersion) {
        EpubMetadata.Builder builder = EpubMetadata.newBuilder();
        Set<String> mapped = new HashSet<>();

        // Core EPUB properties
        MetadataUtils.mapStringField(metadata, Epub.RENDITION_LAYOUT, builder::setRenditionLayout, mapped);
        MetadataUtils.mapStringField(metadata, Epub.VERSION, builder::setVersion, mapped);

        // Technical
        MetadataUtils.mapStringField(metadata, "Content-Type", builder::setMimetype, mapped);

        // Additional helpful fields from core props
        MetadataUtils.mapStringField(metadata, TikaCoreProperties.LANGUAGE, builder::setContentLanguage, mapped);
        MetadataUtils.mapStringField(metadata, TikaCoreProperties.IDENTIFIER, builder::setUniqueIdentifier, mapped);

        // Attempt to enrich with OPF/NAV structure if raw bytes are available in metadata
        byte[] raw = MetadataUtils.tryGetRawBytes(metadata);
        if (raw != null && raw.length > 0) {
            EpubStructureExtractor.enrich(builder, raw);
            // Prevent huge base64 field from being embedded into additional/base fields
            try { metadata.remove("pipe:raw-bytes-b64"); } catch (Exception ignored) {}
        }

        // Additional metadata for anything unmapped
        Struct additional = MetadataUtils.buildAdditionalMetadata(metadata, mapped);
        builder.setAdditionalMetadata(additional);

        // Base fields
        TikaBaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, metadata);
        builder.setBaseFields(base);

        return builder.build();
    }
}
