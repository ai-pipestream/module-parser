package ai.pipestream.module.parser.tika.builders;

import com.google.protobuf.Struct;
import ai.pipestream.parsed.data.climate.v1.ClimateForcastMetadata;
import ai.pipestream.parsed.data.tika.base.v1.TikaBaseFields;
import org.apache.tika.metadata.Metadata;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds ClimateForcastMetadata from Tika Metadata as a minimal MVP: carry base fields
 * and dump all NetCDF/CF metadata to additional_scientific_metadata until we catalog keys.
 */
public final class ClimateForecastMetadataBuilder {

    private ClimateForecastMetadataBuilder() {}

    public static ClimateForcastMetadata build(Metadata md, String parserClass, String tikaVersion) {
        ClimateForcastMetadata.Builder builder = ClimateForcastMetadata.newBuilder();
        Set<String> mapped = new HashSet<>();

        // TODO: Map well-known CF/global attributes incrementally using MetadataUtils when catalogued

        // Additional scientific metadata (preserve fidelity)
        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        builder.setAdditionalScientificMetadata(additional);

        // Base fields
        TikaBaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        builder.setBaseFields(base);

        return builder.build();
    }
}
