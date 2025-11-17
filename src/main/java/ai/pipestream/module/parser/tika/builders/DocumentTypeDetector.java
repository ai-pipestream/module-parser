package ai.pipestream.module.parser.tika.builders;

import ai.pipestream.shaded.tika.metadata.Metadata;
import org.jboss.logging.Logger;

/**
 * Detects document type from Tika metadata to determine which metadata builder to use.
 * <p>
 * Uses MIME type detection with fallbacks to route to the appropriate metadata builder:
 * - PDF → PdfMetadataBuilder
 * - Office → OfficeMetadataBuilder  
 * - Image → ImageMetadataBuilder
 * - Email → EmailMetadataBuilder
 * - Media → MediaMetadataBuilder
 * - HTML → HtmlMetadataBuilder
 * - RTF → RtfMetadataBuilder
 * - Database → DatabaseMetadataBuilder
 * - Font → FontMetadataBuilder
 * - EPUB → EpubMetadataBuilder
 * - WARC → WarcMetadataBuilder
 * - ClimateForcast → ClimateForcastMetadataBuilder
 * - CreativeCommons → CreativeCommonsMetadataBuilder
 * - Generic → GenericMetadataBuilder (fallback)
 */
public class DocumentTypeDetector {
    
    private static final Logger LOG = Logger.getLogger(DocumentTypeDetector.class);
    
    /**
     * Document types that can be detected.
     */
    public enum DocumentType {
        PDF,
        OFFICE,
        IMAGE,
        EMAIL,
        MEDIA,
        HTML,
        RTF,
        DATABASE,
        FONT,
        EPUB,
        WARC,
        CLIMATE_FORECAST,
        CREATIVE_COMMONS,
        GENERIC
    }
    
    /**
     * Detects document type from Tika metadata.
     * 
     * @param metadata Tika metadata object
     * @return DocumentType enum value
     */
    public static DocumentType detect(Metadata metadata) {
        String mimeType = metadata.get("Content-Type");
        String resourceName = metadata.get("resourceName");
        
        LOG.debugf("Detecting document type from MIME type: %s, resource name: %s", mimeType, resourceName);
        
        if (mimeType != null) {
            mimeType = mimeType.toLowerCase();
            
            // PDF Documents
            if (mimeType.contains("pdf")) {
                LOG.debug("Detected PDF document");
                return DocumentType.PDF;
            }
            
            // Office Documents
            if (mimeType.contains("officedocument") || 
                mimeType.contains("msword") ||
                mimeType.contains("ms-excel") ||
                mimeType.contains("ms-powerpoint") ||
                mimeType.contains("opendocument") ||
                mimeType.contains("vnd.ms-") ||
                mimeType.contains("vnd.openxmlformats")) {
                LOG.debug("Detected Office document");
                return DocumentType.OFFICE;
            }
            
            // Image Documents
            if (mimeType.startsWith("image/")) {
                LOG.debug("Detected Image document");
                return DocumentType.IMAGE;
            }
            
            // Email Documents
            if (mimeType.contains("message/") ||
                mimeType.contains("application/vnd.ms-outlook") ||
                mimeType.contains("application/mbox")) {
                LOG.debug("Detected Email document");
                return DocumentType.EMAIL;
            }
            
            // Media Documents
            if (mimeType.startsWith("audio/") ||
                mimeType.startsWith("video/") ||
                mimeType.contains("multimedia")) {
                LOG.debug("Detected Media document");
                return DocumentType.MEDIA;
            }
            
            // HTML Documents
            if (mimeType.contains("text/html") ||
                mimeType.contains("application/xhtml")) {
                LOG.debug("Detected HTML document");
                return DocumentType.HTML;
            }
            
            // RTF Documents
            if (mimeType.contains("rtf") ||
                mimeType.contains("application/rtf")) {
                LOG.debug("Detected RTF document");
                return DocumentType.RTF;
            }
            
            // Database Documents
            if (mimeType.contains("application/vnd.ms-access") ||
                mimeType.contains("application/x-msaccess") ||
                mimeType.contains("application/x-sqlite3") ||
                mimeType.contains("application/vnd.sqlite3") ||
                mimeType.contains("application/dbf") ||
                mimeType.contains("application/x-dbf") ||
                mimeType.contains("database")) {
                LOG.debug("Detected Database document");
                return DocumentType.DATABASE;
            }
            
            // Font Documents
            if (mimeType.contains("font/") ||
                mimeType.contains("application/font") ||
                mimeType.contains("application/x-font")) {
                LOG.debug("Detected Font document");
                return DocumentType.FONT;
            }
            
            // EPUB Documents
            if (mimeType.contains("epub") ||
                mimeType.contains("application/epub+zip")) {
                LOG.debug("Detected EPUB document");
                return DocumentType.EPUB;
            }
            
            // WARC Documents
            if (mimeType.contains("warc") ||
                mimeType.contains("application/warc")) {
                LOG.debug("Detected WARC document");
                return DocumentType.WARC;
            }
            
            // NetCDF/Climate Forecast Documents
            if (mimeType.contains("netcdf") ||
                mimeType.contains("application/x-netcdf") ||
                mimeType.contains("application/netcdf")) {
                LOG.debug("Detected Climate Forecast document");
                return DocumentType.CLIMATE_FORECAST;
            }
        }
        
        // Fallback detection based on file extension
        if (resourceName != null) {
            String lowerName = resourceName.toLowerCase();
            
            if (lowerName.endsWith(".pdf")) {
                LOG.debug("Detected PDF document by extension");
                return DocumentType.PDF;
            }
            
            if (lowerName.endsWith(".doc") || lowerName.endsWith(".docx") ||
                lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") ||
                lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx") ||
                lowerName.endsWith(".odt") || lowerName.endsWith(".ods") ||
                lowerName.endsWith(".odp")) {
                LOG.debug("Detected Office document by extension");
                return DocumentType.OFFICE;
            }
            
            if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                lowerName.endsWith(".tiff") || lowerName.endsWith(".tif") ||
                lowerName.endsWith(".bmp")) {
                LOG.debug("Detected Image document by extension");
                return DocumentType.IMAGE;
            }
            
            if (lowerName.endsWith(".eml") || lowerName.endsWith(".msg") ||
                lowerName.endsWith(".mbox")) {
                LOG.debug("Detected Email document by extension");
                return DocumentType.EMAIL;
            }
            
            if (lowerName.endsWith(".mp3") || lowerName.endsWith(".mp4") ||
                lowerName.endsWith(".avi") || lowerName.endsWith(".wav") ||
                lowerName.endsWith(".flac") || lowerName.endsWith(".mov")) {
                LOG.debug("Detected Media document by extension");
                return DocumentType.MEDIA;
            }
            
            if (lowerName.endsWith(".html") || lowerName.endsWith(".htm") ||
                lowerName.endsWith(".xhtml")) {
                LOG.debug("Detected HTML document by extension");
                return DocumentType.HTML;
            }
            
            if (lowerName.endsWith(".rtf")) {
                LOG.debug("Detected RTF document by extension");
                return DocumentType.RTF;
            }
            
            if (lowerName.endsWith(".mdb") || lowerName.endsWith(".accdb") ||
                lowerName.endsWith(".sqlite") || lowerName.endsWith(".db") ||
                lowerName.endsWith(".dbf")) {
                LOG.debug("Detected Database document by extension");
                return DocumentType.DATABASE;
            }
            
            if (lowerName.endsWith(".ttf") || lowerName.endsWith(".ttc") ||
                lowerName.endsWith(".otf") || lowerName.endsWith(".woff") ||
                lowerName.endsWith(".woff2") || lowerName.endsWith(".afm") ||
                lowerName.endsWith(".pfa") || lowerName.endsWith(".pfb")) {
                LOG.debug("Detected Font document by extension");
                return DocumentType.FONT;
            }
            
            if (lowerName.endsWith(".epub")) {
                LOG.debug("Detected EPUB document by extension");
                return DocumentType.EPUB;
            }
            
            if (lowerName.endsWith(".warc") || lowerName.endsWith(".arc")) {
                LOG.debug("Detected WARC document by extension");
                return DocumentType.WARC;
            }
            
            if (lowerName.endsWith(".nc") || lowerName.endsWith(".netcdf")) {
                LOG.debug("Detected Climate Forecast document by extension");
                return DocumentType.CLIMATE_FORECAST;
            }
        }
        
        // Check for Creative Commons metadata in any document type
        if (hasCreativeCommonsMetadata(metadata)) {
            LOG.debug("Detected Creative Commons metadata");
            return DocumentType.CREATIVE_COMMONS;
        }
        
        // Default to generic
        LOG.debug("Using Generic document type (fallback)");
        return DocumentType.GENERIC;
    }
    
    /**
     * Checks if metadata contains Creative Commons licensing information.
     */
    private static boolean hasCreativeCommonsMetadata(Metadata metadata) {
        String[] allFields = metadata.names();

        for (String field : allFields) {
            String lowerField = field.toLowerCase();
            if (lowerField.contains("license") ||
                lowerField.contains("creative") ||
                lowerField.contains("cc:") ||
                lowerField.contains("rights")) {
                String value = metadata.get(field);
                LOG.debugf("  Field '%s' matches CC pattern, value: %s", field, value);
                if (value != null && value.toLowerCase().contains("creative")) {
                    LOG.infof("Detected Creative Commons via field '%s' = '%s'", field, value);
                    return true;
                }
            }
        }

        LOG.debugf("No Creative Commons metadata detected");
        return false;
    }
}
