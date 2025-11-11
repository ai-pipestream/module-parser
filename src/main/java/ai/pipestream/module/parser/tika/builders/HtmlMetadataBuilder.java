package ai.pipestream.module.parser.tika.builders;

import com.google.protobuf.Struct;
import ai.pipestream.parsed.data.html.v1.HtmlMetadata;
import ai.pipestream.parsed.data.tika.base.v1.TikaBaseFields;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class HtmlMetadataBuilder {

    public static HtmlMetadata build(Metadata md, String parserClass, String tikaVersion) {
        HtmlMetadata.Builder b = HtmlMetadata.newBuilder();
        java.util.Set<String> mapped = new java.util.HashSet<>();

        // Core
        MetadataUtils.mapStringField(md, TikaCoreProperties.TITLE, b::setTitle, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CREATOR, b::setCreator, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.DESCRIPTION, b::setDescription, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.SUBJECT, b::setSubject, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.LANGUAGE, b::setLanguage, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.PUBLISHER, b::setPublisher, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.RIGHTS, b::setRights, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.FORMAT, b::setFormat, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.IDENTIFIER, b::setIdentifier, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CONTRIBUTOR, b::setContributor, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.COVERAGE, b::setCoverage, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.RELATION, b::setRelation, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.SOURCE, b::setSource, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.TYPE, b::setType, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.CREATED, b::setCreated, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.MODIFIED, b::setModified, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CREATOR_TOOL, b::setCreatorTool, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.COMMENTS, b::setComments, mapped);

        // Content/encoding
        MetadataUtils.mapStringField(md, "Content-Type", b::setContentType, mapped);
        MetadataUtils.mapStringField(md, Metadata.CONTENT_ENCODING, b::setContentEncoding, mapped);
        MetadataUtils.mapStringField(md, Metadata.CONTENT_LANGUAGE, b::setContentLanguage, mapped);
        MetadataUtils.mapStringField(md, Metadata.CONTENT_LOCATION, b::setContentLocation, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CONTENT_TYPE_HINT, b::setContentTypeHint, mapped);

        // Geo (ICBM)
        MetadataUtils.mapStringField(md, "ICBM", b::setIcbm, mapped);
        MetadataUtils.mapStringField(md, Metadata.LATITUDE, b::setLatitude, mapped);
        MetadataUtils.mapStringField(md, Metadata.LONGITUDE, b::setLongitude, mapped);

        // Common meta tags and social
        mapMeta(md, b, mapped);

        // Links
        MetadataUtils.mapStringField(md, "html:link:canonical", b::setCanonicalUrl, mapped);
        MetadataUtils.mapStringField(md, "html:link:alternate", b::setAlternateUrl, mapped);
        MetadataUtils.mapStringField(md, "html:link:stylesheet", b::setStylesheetUrl, mapped);
        MetadataUtils.mapStringField(md, "html:link:icon", b::setIconUrl, mapped);
        MetadataUtils.mapStringField(md, "html:link:rss", b::setRssUrl, mapped);
        MetadataUtils.mapStringField(md, "html:link:atom", b::setAtomUrl, mapped);

        // Data URIs discovered
        MetadataUtils.mapRepeatedStringField(md, "html:data-uri", b::addAllDataUriSchemes, mapped);

        // Security & parsing
        MetadataUtils.mapBooleanField(md, TikaCoreProperties.HAS_SIGNATURE, b::setHasSignature, mapped);
        MetadataUtils.mapBooleanField(md, TikaCoreProperties.IS_ENCRYPTED, b::setIsEncrypted, mapped);
        MetadataUtils.mapRepeatedStringField(md, TikaCoreProperties.TIKA_PARSED_BY, b::addAllParsedBy, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.TIKA_DETECTED_LANGUAGE, b::setDetectedLanguage, mapped);
        MetadataUtils.mapDoubleField(md, TikaCoreProperties.TIKA_DETECTED_LANGUAGE_CONFIDENCE_RAW, b::setDetectedLanguageConfidence, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.ENCODING_DETECTOR, b::setEncodingDetector, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.DETECTED_ENCODING, b::setDetectedEncoding, mapped);

        // Resource
        MetadataUtils.mapStringField(md, TikaCoreProperties.RESOURCE_NAME_KEY, b::setResourceName, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.ORIGINAL_RESOURCE_NAME, b::setOriginalResourceName, mapped);

        // Additional
        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        b.setAdditionalMetadata(additional);

        // Base
        TikaBaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        b.setBaseFields(base);

        return b.build();
    }

    private static void mapMeta(Metadata md, HtmlMetadata.Builder b, java.util.Set<String> mapped) {
        // Standard meta
        MetadataUtils.mapStringField(md, "html:meta:description", b::setMetaDescription, mapped);
        MetadataUtils.mapStringField(md, "html:meta:keywords", b::setMetaKeywords, mapped);
        MetadataUtils.mapStringField(md, "html:meta:author", b::setMetaAuthor, mapped);
        MetadataUtils.mapStringField(md, "html:meta:generator", b::setMetaGenerator, mapped);
        MetadataUtils.mapStringField(md, "html:meta:robots", b::setMetaRobots, mapped);
        MetadataUtils.mapStringField(md, "html:meta:viewport", b::setMetaViewport, mapped);
        MetadataUtils.mapStringField(md, "html:meta:charset", b::setMetaCharset, mapped);
        MetadataUtils.mapStringField(md, "html:meta:refresh", b::setMetaRefresh, mapped);

        // Open Graph
        MetadataUtils.mapStringField(md, "html:meta:og:title", b::setOgTitle, mapped);
        MetadataUtils.mapStringField(md, "html:meta:og:description", b::setOgDescription, mapped);
        MetadataUtils.mapStringField(md, "html:meta:og:image", b::setOgImage, mapped);
        MetadataUtils.mapStringField(md, "html:meta:og:url", b::setOgUrl, mapped);
        MetadataUtils.mapStringField(md, "html:meta:og:type", b::setOgType, mapped);
        MetadataUtils.mapStringField(md, "html:meta:og:site_name", b::setOgSiteName, mapped);

        // Twitter Card
        MetadataUtils.mapStringField(md, "html:meta:twitter:card", b::setTwitterCard, mapped);
        MetadataUtils.mapStringField(md, "html:meta:twitter:site", b::setTwitterSite, mapped);
        MetadataUtils.mapStringField(md, "html:meta:twitter:creator", b::setTwitterCreator, mapped);
        MetadataUtils.mapStringField(md, "html:meta:twitter:title", b::setTwitterTitle, mapped);
        MetadataUtils.mapStringField(md, "html:meta:twitter:description", b::setTwitterDescription, mapped);
        MetadataUtils.mapStringField(md, "html:meta:twitter:image", b::setTwitterImage, mapped);

        // Dublin Core meta
        MetadataUtils.mapStringField(md, "html:meta:dc:title", b::setDcTitle, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:creator", b::setDcCreator, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:subject", b::setDcSubject, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:description", b::setDcDescription, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:publisher", b::setDcPublisher, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:contributor", b::setDcContributor, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:date", b::setDcDate, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:type", b::setDcType, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:format", b::setDcFormat, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:identifier", b::setDcIdentifier, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:source", b::setDcSource, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:language", b::setDcLanguage, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:relation", b::setDcRelation, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:coverage", b::setDcCoverage, mapped);
        MetadataUtils.mapStringField(md, "html:meta:dc:rights", b::setDcRights, mapped);

        // Script source (from HtmlHandler when scripts extracted)
        MetadataUtils.mapStringField(md, "html:script:src", b::setScriptSource, mapped);
    }
}
