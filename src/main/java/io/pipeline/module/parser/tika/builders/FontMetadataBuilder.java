package io.pipeline.module.parser.tika.builders;

import com.google.protobuf.Struct;
import io.pipeline.parsed.data.tika.font.v1.FontMetadata;
import io.pipeline.parsed.data.tika.base.v1.TikaBaseFields;
import org.apache.tika.metadata.Font;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import java.util.HashSet;
import java.util.Set;

public class FontMetadataBuilder {

    public static FontMetadata build(Metadata md, String parserClass, String tikaVersion) {
        FontMetadata.Builder b = FontMetadata.newBuilder();
        Set<String> mapped = new HashSet<>();

        // Basic font name(s)
        MetadataUtils.mapRepeatedStringField(md, Font.FONT_NAME, names -> {
            if (names.iterator().hasNext()) {
                String first = names.iterator().next();
                b.setFontName(first);
            }
        }, mapped);

        // Known fields Tika often sets for fonts (best effort via raw keys)
        MetadataUtils.mapStringField(md, TikaCoreProperties.RESOURCE_NAME_KEY, b::setOriginalFilename, mapped);
        MetadataUtils.mapStringField(md, "Content-Type", b::setMimeType, mapped);

        // Fallbacks for common cases when we bypass full Tika parsing
        if (!b.hasOriginalFilename()) {
            String rn = md.get("resourceName");
            if (rn != null && !rn.isEmpty()) {
                b.setOriginalFilename(rn);
            }
        }
        if (!b.hasFontName()) {
            String base = null;
            if (b.hasOriginalFilename()) {
                base = b.getOriginalFilename();
            } else {
                String rn = md.get("resourceName");
                if (rn != null && !rn.isEmpty()) base = rn;
            }
            if (base != null && !base.isEmpty()) {
                int dot = base.lastIndexOf('.');
                if (dot > 0) {
                    base = base.substring(0, dot);
                }
                if (!base.isEmpty()) {
                    b.setFontName(base);
                }
            }
        }

        // Additional metadata dump
        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        b.setAdditionalMetadata(additional);

        // Base fields
        TikaBaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        b.setBaseFields(base);

        return b.build();
    }
}
