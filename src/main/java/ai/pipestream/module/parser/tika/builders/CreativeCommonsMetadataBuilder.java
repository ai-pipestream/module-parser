package ai.pipestream.module.parser.tika.builders;

import com.google.protobuf.Struct;
import ai.pipestream.parsed.data.creative_commons.v1.CreativeCommonsMetadata;
import ai.pipestream.parsed.data.tika.base.v1.TikaBaseFields;
import ai.pipestream.shaded.tika.metadata.Metadata;
import ai.pipestream.shaded.tika.metadata.XMPRights;

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

        org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(CreativeCommonsMetadataBuilder.class);
        LOG.debugf("Building CreativeCommonsMetadata, checking for XMPRights fields...");
        LOG.debugf("  WebStatement in metadata: %s", metadata.get(XMPRights.WEB_STATEMENT));
        LOG.debugf("  UsageTerms in metadata: %s", metadata.get(XMPRights.USAGE_TERMS));
        LOG.debugf("  Marked in metadata: %s", metadata.get(XMPRights.MARKED));

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
