package io.pipeline.module.parser.tika.builders;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import io.pipeline.parsed.data.warc.v1.WarcMetadata;
import io.pipeline.parsed.data.warc.v1.WarcHttpHeader;
import io.pipeline.parsed.data.tika.base.v1.TikaBaseFields;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.WARC;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WarcMetadataBuilder {

    public static WarcMetadata build(Metadata md, String parserClass, String tikaVersion) {
        WarcMetadata.Builder b = WarcMetadata.newBuilder();
        Set<String> mapped = new HashSet<>();

        mapWarc(md, b, mapped);
        mapHttp(md, b, mapped);
        mapContentAnalysis(md, b, mapped);
        mapArchiveProcessing(md, b, mapped);

        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        b.setAdditionalMetadata(additional);

        TikaBaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        b.setBaseFields(base);

        return b.build();
    }

    private static void mapWarc(Metadata md, WarcMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapRepeatedStringField(md, WARC.WARC_WARNING, b::addAllWarcWarnings, mapped);
        MetadataUtils.mapStringField(md, WARC.WARC_RECORD_CONTENT_TYPE, b::setWarcRecordContentType, mapped);
        MetadataUtils.mapStringField(md, WARC.WARC_PAYLOAD_CONTENT_TYPE, b::setWarcPayloadContentType, mapped);
        MetadataUtils.mapStringField(md, WARC.WARC_RECORD_ID, b::setWarcRecordId, mapped);
        // Also map from literal header if present
        MetadataUtils.mapStringField(md, "warc:WARC-Record-ID", b::setWarcRecordId, mapped);

        // Core headers as literal keys from WARCParser
        MetadataUtils.mapStringField(md, "warc:WARC-Type", b::setWarcType, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Target-URI", b::setWarcTargetUri, mapped);
        mapTimestampFromString(md, "warc:WARC-Date", b::setWarcDate, mapped);
        MetadataUtils.mapLongField(md, Metadata.CONTENT_LENGTH, b::setWarcContentLength, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Filename", b::setWarcFilename, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Refers-To", b::setWarcRefersTo, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Concurrent-To", b::setWarcConcurrentTo, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Warcinfo-ID", b::setWarcWarcinfoId, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-IP-Address", b::setWarcIpAddress, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Block-Digest", b::setWarcBlockDigest, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Payload-Digest", b::setWarcPayloadDigest, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Truncated", b::setWarcTruncated, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Identified-Payload-Type", b::setWarcIdentifiedPayloadType, mapped);
    }

    private static void mapHttp(Metadata md, WarcMetadata.Builder b, Set<String> mapped) {
        // Status
        MetadataUtils.mapIntField(md, "warc:http:status", b::setHttpStatusCode, mapped);
        MetadataUtils.mapStringField(md, "warc:http:status:reason", b::setHttpStatusReason, mapped);

        // Headers (collect all warc:http:* excluding status fields)
        List<WarcHttpHeader> headers = new ArrayList<>();
        for (String name : md.names()) {
            if (!name.startsWith("warc:http:")) continue;
            if (name.equals("warc:http:status") || name.equals("warc:http:status:reason")) continue;
            for (String v : md.getValues(name)) {
                WarcHttpHeader h = WarcHttpHeader.newBuilder()
                        .setName(name.substring("warc:http:".length()))
                        .setValue(v)
                        .build();
                headers.add(h);
            }
        }
        if (!headers.isEmpty()) {
            b.addAllHttpHeaders(headers);
        }
    }

    private static void mapContentAnalysis(Metadata md, WarcMetadata.Builder b, Set<String> mapped) {
        // Content language / encoding might be under various keys; map standard ones if present
        MetadataUtils.mapStringField(md, "Content-Language", b::setContentLanguage, mapped);
        MetadataUtils.mapStringField(md, "Content-Encoding", b::setContentEncoding, mapped);
    }

    private static void mapArchiveProcessing(Metadata md, WarcMetadata.Builder b, Set<String> mapped) {
        // Best-effort mapping; many of these may not be present
        MetadataUtils.mapStringField(md, "warc:software", b::setWarcCreatedBy, mapped);
        MetadataUtils.mapStringField(md, "warc:WARC-Format", b::setWarcFormatVersion, mapped);
        MetadataUtils.mapStringField(md, "warc:collection", b::setWarcCollection, mapped);
        MetadataUtils.mapStringField(md, "warc:crawl", b::setWarcCrawlId, mapped);
        MetadataUtils.mapStringField(md, "warc:robots", b::setWarcRobotPolicy, mapped);
    }

    private static void mapTimestampFromString(Metadata md, String key, java.util.function.Consumer<Timestamp> setter, Set<String> mapped) {
        String v = md.get(key);
        if (v == null || v.trim().isEmpty()) return;
        try {
            Instant i = Instant.parse(v.trim());
            setter.accept(Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build());
            mapped.add(key);
        } catch (Exception ignore) {
        }
    }
}
