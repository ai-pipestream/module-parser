package ai.pipestream.module.parser.tika.builders;

import com.google.protobuf.Struct;
import ai.pipestream.parsed.data.creative_commons.v1.CreativeCommonsMetadata;
import ai.pipestream.parsed.data.tika.base.v1.TikaBaseFields;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPRights;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds CreativeCommonsMetadata from Tika Metadata. Overlay metadata across types.
 */
public final class CreativeCommonsMetadataBuilder {

    private CreativeCommonsMetadataBuilder() {}

    public static CreativeCommonsMetadata build(Metadata metadata, String parserClass, String tikaVersion) {
        CreativeCommonsMetadata.Builder builder = CreativeCommonsMetadata.newBuilder();
        Set<String> mapped = new HashSet<>();

        // XMPRights
        MetadataUtils.mapStringField(metadata, XMPRights.CERTIFICATE, builder::setRightsCertificate, mapped);
        MetadataUtils.mapBooleanField(metadata, XMPRights.MARKED, builder::setRightsMarked, mapped);
        MetadataUtils.mapRepeatedStringField(metadata, XMPRights.OWNER, builder::addAllRightsOwners, mapped);
        MetadataUtils.mapStringField(metadata, XMPRights.USAGE_TERMS, builder::setUsageTerms, mapped);
        MetadataUtils.mapStringField(metadata, XMPRights.WEB_STATEMENT, builder::setWebStatement, mapped);

        // Additional (any cc:* or license* fields) left in additional_metadata
        Struct additional = MetadataUtils.buildAdditionalMetadata(metadata, mapped);
        builder.setAdditionalRightsMetadata(additional);

        TikaBaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, metadata);
        builder.setBaseFields(base);

        return builder.build();
    }
}
