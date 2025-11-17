package ai.pipestream.module.parser.tika.builders;

import com.google.protobuf.Struct;
import ai.pipestream.parsed.data.database.v1.DatabaseMetadata;
import ai.pipestream.parsed.data.tika.base.v1.TikaBaseFields;
import ai.pipestream.shaded.tika.metadata.Metadata;
import ai.pipestream.shaded.tika.metadata.TikaCoreProperties;
import ai.pipestream.shaded.tika.metadata.Database; // Tika's Database metadata interface

import java.util.HashSet;
import java.util.Set;

/**
 * Builds strongly-typed DatabaseMetadata from Tika Metadata.
 *
 * Maps commonly available database fields (table/column names, counts) and preserves all
 * remaining keys in additional_metadata for fidelity.
 */
public final class DatabaseMetadataBuilder {

    private DatabaseMetadataBuilder() {}

    public static DatabaseMetadata build(Metadata md, String parserClass, String tikaVersion) {
        DatabaseMetadata.Builder builder = DatabaseMetadata.newBuilder();
        Set<String> mapped = new HashSet<>();

        // Core document fields
        mapCore(md, builder, mapped);

        // Database table/column lists and counts
        mapDbCore(md, builder, mapped);

        // Content/resource hints
        MetadataUtils.mapStringField(md, "Content-Type", builder::setContentType, mapped);
        MetadataUtils.mapStringField(md, "Content-Length", builder::setContentLength, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.RESOURCE_NAME_KEY, builder::setResourceName, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.ORIGINAL_RESOURCE_NAME, builder::setOriginalResourceName, mapped);

        // Additional metadata
        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        builder.setAdditionalMetadata(additional);

        // Base fields
        TikaBaseFields baseFields = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        builder.setBaseFields(baseFields);

        return builder.build();
    }

    private static void mapCore(Metadata md, DatabaseMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, TikaCoreProperties.TITLE, b::setTitle, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CREATOR, b::setCreator, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.DESCRIPTION, b::setDescription, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.SUBJECT, b::setSubject, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.LANGUAGE, b::setLanguage, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.FORMAT, b::setFormat, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.IDENTIFIER, b::setIdentifier, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.COMMENTS, b::setComments, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.CREATED, b::setCreated, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.MODIFIED, b::setModified, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CREATOR_TOOL, b::setCreatorTool, mapped);
    }

    private static void mapDbCore(Metadata md, DatabaseMetadata.Builder b, Set<String> mapped) {
        try {
            // Table names and column names (repeated)
            MetadataUtils.mapRepeatedStringField(md, Database.TABLE_NAME, b::addAllTableNames, mapped);
            MetadataUtils.mapRepeatedStringField(md, Database.COLUMN_NAME, b::addAllColumnNames, mapped);

            // Total counts
            MetadataUtils.mapIntField(md, Database.ROW_COUNT, b::setTotalRowCount, mapped);
            MetadataUtils.mapIntField(md, Database.COLUMN_COUNT, b::setTotalColumnCount, mapped);
        } catch (Throwable ignored) {
            // If Tika's Database class isn't present for some formats, skip quietly
        }
    }
}
