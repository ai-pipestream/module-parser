package ai.pipestream.module.parser.tika.builders;

import com.google.protobuf.Struct;
import ai.pipestream.parsed.data.office.v1.OfficeMetadata;
import ai.pipestream.parsed.data.tika.base.v1.TikaBaseFields;
import ai.pipestream.shaded.tika.metadata.Metadata;
import ai.pipestream.shaded.tika.metadata.Office;
import ai.pipestream.shaded.tika.metadata.OfficeOpenXMLCore;
import ai.pipestream.shaded.tika.metadata.OfficeOpenXMLExtended;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.Set;

public class OfficeMetadataBuilder {
    private static final Logger LOG = Logger.getLogger(OfficeMetadataBuilder.class);

    public static OfficeMetadata build(Metadata tikaMetadata, String parserClass, String tikaVersion) {
        OfficeMetadata.Builder builder = OfficeMetadata.newBuilder();
        Set<String> mapped = new HashSet<>();

        mapOfficeCore(tikaMetadata, builder, mapped);
        mapOfficeOpenXMLCore(tikaMetadata, builder, mapped);
        mapOfficeOpenXMLExtended(tikaMetadata, builder, mapped);
        mapCommonFields(tikaMetadata, builder, mapped);

        Struct additional = MetadataUtils.buildAdditionalMetadata(tikaMetadata, mapped);
        builder.setAdditionalMetadata(additional);

        TikaBaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, tikaMetadata);
        builder.setBaseFields(base);

        return builder.build();
    }

    private static void mapOfficeCore(Metadata md, OfficeMetadata.Builder b, Set<String> mapped) {
        // Keywords (also candidates for dublin_core.subjects via higher-level DC builder)
        MetadataUtils.mapRepeatedStringField(md, Office.KEYWORDS, b::addAllKeywords, mapped);

        // Authors & dates
        MetadataUtils.mapStringField(md, Office.INITIAL_AUTHOR, b::setInitialAuthor, mapped);
        MetadataUtils.mapStringField(md, Office.LAST_AUTHOR, b::setLastAuthor, mapped);
        MetadataUtils.mapRepeatedStringField(md, Office.AUTHOR, b::addAllAuthor, mapped);
        MetadataUtils.mapTimestampFieldWithRaw(md, Office.CREATION_DATE, b::setCreationDate, b::setCreationDateRaw, mapped);
        MetadataUtils.mapTimestampFieldWithRaw(md, Office.SAVE_DATE, b::setSaveDate, b::setSaveDateRaw, mapped);
        MetadataUtils.mapTimestampFieldWithRaw(md, Office.PRINT_DATE, b::setPrintDate, b::setPrintDateRaw, mapped);

        // Counts/statistics
        MetadataUtils.mapIntField(md, Office.SLIDE_COUNT, b::setSlideCount, mapped);
        MetadataUtils.mapIntField(md, Office.PAGE_COUNT, b::setPageCount, mapped);
        MetadataUtils.mapIntField(md, Office.PARAGRAPH_COUNT, b::setParagraphCount, mapped);
        MetadataUtils.mapIntField(md, Office.LINE_COUNT, b::setLineCount, mapped);
        MetadataUtils.mapIntField(md, Office.WORD_COUNT, b::setWordCount, mapped);
        MetadataUtils.mapIntField(md, Office.CHARACTER_COUNT, b::setCharacterCount, mapped);
        MetadataUtils.mapIntField(md, Office.CHARACTER_COUNT_WITH_SPACES, b::setCharacterCountWithSpaces, mapped);
        MetadataUtils.mapIntField(md, Office.TABLE_COUNT, b::setTableCount, mapped);
        MetadataUtils.mapIntField(md, Office.IMAGE_COUNT, b::setImageCount, mapped);
        MetadataUtils.mapIntField(md, Office.OBJECT_COUNT, b::setObjectCount, mapped);

        // MS Office specifics
        MetadataUtils.mapStringField(md, Office.PROG_ID, b::setProgId, mapped);
        MetadataUtils.mapStringField(md, Office.OCX_NAME, b::setOcxName, mapped);
        MetadataUtils.mapStringField(md, Office.EMBEDDED_STORAGE_CLASS_ID, b::setEmbeddedStorageClassId, mapped);

        // Excel specifics
        MetadataUtils.mapBooleanField(md, Office.HAS_HIDDEN_SHEETS, b::setHasHiddenSheets, mapped);
        MetadataUtils.mapBooleanField(md, Office.HAS_HIDDEN_COLUMNS, b::setHasHiddenColumns, mapped);
        MetadataUtils.mapBooleanField(md, Office.HAS_HIDDEN_ROWS, b::setHasHiddenRows, mapped);
        MetadataUtils.mapBooleanField(md, Office.HAS_VERY_HIDDEN_SHEETS, b::setHasVeryHiddenSheets, mapped);
        MetadataUtils.mapRepeatedStringField(md, Office.HIDDEN_SHEET_NAMES, b::addAllHiddenSheetNames, mapped);
        MetadataUtils.mapRepeatedStringField(md, Office.VERY_HIDDEN_SHEET_NAMES, b::addAllVeryHiddenSheetNames, mapped);
        MetadataUtils.mapBooleanField(md, Office.PROTECTED_WORKSHEET, b::setProtectedWorksheet, mapped);
        MetadataUtils.mapStringField(md, Office.WORKBOOK_CODENAME, b::setWorkbookCodename, mapped);

        // Comments and collaboration
        MetadataUtils.mapBooleanField(md, Office.HAS_COMMENTS, b::setHasComments, mapped);
        MetadataUtils.mapRepeatedStringField(md, Office.COMMENT_PERSONS, b::addAllCommentPersons, mapped);

        // PowerPoint specifics
        MetadataUtils.mapBooleanField(md, Office.HAS_HIDDEN_SLIDES, b::setHasHiddenSlides, mapped);
        MetadataUtils.mapIntField(md, Office.NUM_HIDDEN_SLIDES, b::setNumHiddenSlides, mapped);
        MetadataUtils.mapBooleanField(md, Office.HAS_ANIMATIONS, b::setHasAnimations, mapped);

        // Word specifics
        MetadataUtils.mapBooleanField(md, Office.HAS_HIDDEN_TEXT, b::setHasHiddenText, mapped);
        MetadataUtils.mapBooleanField(md, Office.HAS_TRACK_CHANGES, b::setHasTrackChanges, mapped);
    }

    private static void mapOfficeOpenXMLCore(Metadata md, OfficeMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, OfficeOpenXMLCore.CATEGORY, b::setCategory, mapped);
        MetadataUtils.mapStringField(md, OfficeOpenXMLCore.CONTENT_STATUS, b::setContentStatus, mapped);
        MetadataUtils.mapStringField(md, OfficeOpenXMLCore.LAST_MODIFIED_BY, b::setLastModifiedBy, mapped);
        MetadataUtils.mapTimestampFieldWithRaw(md, OfficeOpenXMLCore.LAST_PRINTED, b::setLastPrinted, b::setLastPrintedRaw, mapped);
        MetadataUtils.mapStringField(md, OfficeOpenXMLCore.REVISION, b::setRevision, mapped);
        MetadataUtils.mapStringField(md, OfficeOpenXMLCore.VERSION, b::setVersion, mapped);
    }

    private static void mapOfficeOpenXMLExtended(Metadata md, OfficeMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, OfficeOpenXMLExtended.TEMPLATE, b::setTemplate, mapped);
        MetadataUtils.mapRepeatedStringField(md, OfficeOpenXMLExtended.MANAGER, b::addAllManager, mapped);
        MetadataUtils.mapStringField(md, OfficeOpenXMLExtended.COMPANY, b::setCompany, mapped);
        MetadataUtils.mapStringField(md, OfficeOpenXMLExtended.PRESENTATION_FORMAT, b::setPresentationFormat, mapped);
        MetadataUtils.mapIntField(md, OfficeOpenXMLExtended.NOTES, b::setNotes, mapped);

        // TOTAL_TIME: capture raw and seconds if possible
        MetadataUtils.mapStringField(md, OfficeOpenXMLExtended.TOTAL_TIME, b::setTotalTimeRaw, mapped);
        String raw = md.get(OfficeOpenXMLExtended.TOTAL_TIME);
        if (raw != null && !raw.trim().isEmpty()) {
            Integer secs = tryParseDurationSeconds(raw.trim());
            if (secs != null) {
                b.setTotalTimeSeconds(secs);
                mapped.add(OfficeOpenXMLExtended.TOTAL_TIME.getName());
            }
        }

        MetadataUtils.mapIntField(md, OfficeOpenXMLExtended.HIDDEN_SLIDES, b::setHiddenSlides, mapped);
        MetadataUtils.mapStringField(md, OfficeOpenXMLExtended.APPLICATION, b::setApplication, mapped);
        MetadataUtils.mapStringField(md, OfficeOpenXMLExtended.APP_VERSION, b::setAppVersion, mapped);
        MetadataUtils.mapIntField(md, OfficeOpenXMLExtended.DOC_SECURITY, b::setDocSecurity, mapped);
        MetadataUtils.mapStringField(md, OfficeOpenXMLExtended.DOC_SECURITY_STRING, b::setDocSecurityString, mapped);
        MetadataUtils.mapRepeatedStringField(md, OfficeOpenXMLExtended.COMMENTS, b::addAllExtendedComments, mapped);
    }

    private static void mapCommonFields(Metadata md, OfficeMetadata.Builder b, Set<String> mapped) {
        // Content type and security/signatures
        MetadataUtils.mapStringField(md, "Content-Type", b::setContentType, mapped);
    }

    private static Integer tryParseDurationSeconds(String isoLike) {
        try {
            // Normalize formats like PT02H03M24S or plain seconds strings
            if (isoLike.startsWith("PT")) {
                // Fallback: rough parse for PTxxHxxMxxS
                java.time.Duration d = java.time.Duration.parse(isoLike);
                long secs = d.getSeconds();
                if (secs <= Integer.MAX_VALUE) return (int) secs;
                return null;
            } else {
                return Integer.parseInt(isoLike);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
