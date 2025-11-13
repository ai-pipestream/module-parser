# Tika Metadata Enhancement TODO


## 🎯 Project Overview
**ENHANCE** (not replace) the existing Tika parser to extract comprehensive metadata into our new protobuf structures **in addition to** the current functionality.

## 📊 Current Architecture (KEEP!)
- ✅ **DocumentParser.java**: Extracts title, body, basic metadata → `PipeDoc`
- ✅ **ParserServiceImpl.java**: gRPC service that processes documents
- ✅ **Existing functionality**: Works well for content extraction and search

## 🎯 Enhancement Goal
Add comprehensive metadata extraction alongside existing functionality:
- **Current**: `Tika Metadata` → `PipeDoc` (title, body, basic metadata struct)
- **Enhanced**: `Tika Metadata` → `PipeDoc` + `TikaResponse` (1,330+ strongly-typed fields)

## 📋 **CRITICAL: See TIKA_INTERFACE_MAPPING.md**
**Before implementing any builder, consult `TIKA_INTERFACE_MAPPING.md` which contains:**
- ✅ **Verified Tika interfaces** (checked against actual source code)
- ✅ **Actual property mappings** (no assumptions, only real properties)
- ✅ **Interface to protobuf mappings** for all 14 document types
- ✅ **Implementation guidelines** based on what Tika actually provides

## 📊 Progress Tracker
- **Total Metadata Extractors**: 14
- **Completed**: 0/14 (0%)
- **In Progress**: 0
- **Remaining**: 14

---

## 🏗️ Implementation Pattern

### Current Flow (KEEP!)
```java
ByteString content → DocumentParser.parseDocument() → PipeDoc
                                                   ↓
                                            (title, body, basic metadata)
```

### Enhanced Flow (ADD!)
```java
ByteString content → DocumentParser.parseDocument() → PipeDoc + TikaResponse
                                                   ↓              ↓
                                    (title, body, basic metadata) (comprehensive metadata)
```

---

## 📋 Implementation Tasks

### ✅ COMPLETED EXTRACTORS (0/14)
*None yet - ready to start!*

---

### 🚧 IN PROGRESS EXTRACTORS (0/14)
*None currently in progress*

---

### ⏳ PENDING EXTRACTORS (14/14)

#### 1. 🎯 **NEXT UP: PDF Metadata Extractor** - Priority 1
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐ (Medium - well-defined interface)
- **Verified Tika Interfaces**: 
  - ✅ `PDF.java` - 50+ actual properties (DOC_INFO_*, PDF_VERSION, IS_ENCRYPTED, HAS_XFA, etc.)
  - ✅ `XMPPDF.java` - XMP PDF-specific properties  
  - ✅ `AccessPermissions.java` - PDF security permissions
- **Destination Entity**: 
  - Protobuf: `io.pipeline.parsed.data.pdf.v1.PdfMetadata`
- **Implementation**: Create `PdfMetadataBuilder.java`
- **⚠️ CRITICAL**: Use **only** properties that actually exist in PDF.java interface
- **Key Real Properties**:
  ```java
  // VERIFIED properties from PDF.java
  PDF.DOC_INFO_TITLE → title
  PDF.DOC_INFO_AUTHOR → author
  PDF.DOC_INFO_SUBJECT → subject
  PDF.DOC_INFO_KEYWORDS → keywords
  PDF.DOC_INFO_CREATOR → creator
  PDF.DOC_INFO_PRODUCER → producer
  PDF.DOC_INFO_CREATED → creation_date
  PDF.DOC_INFO_MODIFICATION_DATE → modification_date
  PDF.PDF_VERSION → pdf_version
  PDF.PDFA_VERSION → pdfa_version
  PDF.IS_ENCRYPTED → is_encrypted
  PDF.HAS_XFA → has_xfa
  PDF.HAS_ACROFORM_FIELDS → has_acroform_fields
  PDF.HAS_MARKED_CONTENT → has_marked_content
  PDF.HAS_COLLECTION → has_collection
  PDF.HAS_3D → has_3d
  PDF.NUM_3D_ANNOTATIONS → num_3d_annotations
  // ... see TIKA_INTERFACE_MAPPING.md for complete list
  ```

#### 2. **Office Metadata Extractor** - Priority 1  
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - multiple interfaces)
- **Verified Tika Interfaces**:
  - ✅ `Office.java` - Basic Office properties
  - ✅ `OfficeOpenXMLCore.java` - OOXML core metadata
  - ✅ `OfficeOpenXMLExtended.java` - OOXML extended metadata
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.office.v1.OfficeMetadata`
- **Implementation**: Create `OfficeMetadataBuilder.java`

#### 3. **Image Metadata Extractor** - Priority 1
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐⭐ (Very High - multiple image interfaces)
- **Verified Tika Interfaces**:
  - ✅ `TIFF.java` - TIFF-specific properties
  - ✅ `IPTC.java` - IPTC metadata standard
  - ✅ `Photoshop.java` - Photoshop metadata
  - ✅ `XMP.java` - XMP basic properties
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.image.v1.ImageMetadata`
- **Implementation**: Create `ImageMetadataBuilder.java`

#### 4. **Email Metadata Extractor** - Priority 2
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - email complexity)
- **Verified Tika Interfaces**:
  - ✅ `Message.java` - Email message properties
  - ✅ `MAPI.java` - Outlook/MAPI properties
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.email.v1.EmailMetadata`
- **Implementation**: Create `EmailMetadataBuilder.java`

#### 5. **Media Metadata Extractor** - Priority 2
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - media formats)
- **Verified Tika Interfaces**:
  - ✅ `XMPDM.java` - XMP Digital Media properties
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.media.v1.MediaMetadata`
- **Implementation**: Create `MediaMetadataBuilder.java`

#### 6. **HTML Metadata Extractor** - Priority 2
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐ (Medium - web metadata)
- **Verified Tika Interfaces**:
  - ✅ `HTML.java` - HTML metadata properties
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.html.v1.HtmlMetadata`
- **Implementation**: Create `HtmlMetadataBuilder.java`

#### 7. **RTF Metadata Extractor** - Priority 3
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐ (Medium - RTF format)
- **Verified Tika Interfaces**:
  - ✅ `RTFMetadata.java` - RTF-specific properties
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.rtf.v1.RtfMetadata`
- **Implementation**: Create `RtfMetadataBuilder.java`

#### 8. **Database Metadata Extractor** - Priority 3
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - database schema)
- **Verified Tika Interfaces**:
  - ✅ `Database.java` - Database file properties
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.database.v1.DatabaseMetadata`
- **Implementation**: Create `DatabaseMetadataBuilder.java`

#### 9. **Font Metadata Extractor** - Priority 3
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐ (Medium - font metrics)
- **Verified Tika Interfaces**:
  - ✅ `Font.java` - Font file properties (minimal interface)
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.tika.font.v1.FontMetadata`
- **Implementation**: Create `FontMetadataBuilder.java`

#### 10. **EPUB Metadata Extractor** - Priority 2 (Critical for Chunking!)
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐⭐ (Very High - structural metadata)
- **Verified Tika Interfaces**:
  - ✅ `Epub.java` - EPUB book properties (minimal interface)
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.epub.v1.EpubMetadata`
- **Implementation**: Create `EpubMetadataBuilder.java`
- **Special Notes**: **CRITICAL FOR CHUNKING** - structural metadata needed

#### 11. **WARC Metadata Extractor** - Priority 2 (Important for Preservarca!)
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐⭐ (Very High - web archive complexity)
- **Verified Tika Interfaces**:
  - ✅ `WARC.java` - Web archive properties
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.warc.v1.WarcMetadata`
- **Implementation**: Create `WarcMetadataBuilder.java`
- **Special Notes**: **IMPORTANT FOR PRESERVARCA** - web archive metadata

#### 12. **ClimateForcast Metadata Extractor** - Priority 3
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐⭐ (Very High - scientific data)
- **Verified Tika Interfaces**:
  - ✅ `ClimateForcast.java` - NetCDF/Climate properties (16 CF Convention properties)
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.climate.v1.ClimateForcastMetadata`
- **Implementation**: Create `ClimateForcastMetadataBuilder.java`

#### 13. **CreativeCommons Metadata Extractor** - Priority 4
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - rights management)
- **Verified Tika Interfaces**:
  - ✅ `CreativeCommons.java` - CC licensing properties (3 properties)
  - ✅ `XMPRights.java` - XMP rights management (5 properties)
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.creative_commons.v1.CreativeCommonsMetadata`
- **Implementation**: Create `CreativeCommonsMetadataBuilder.java`

#### 14. **Generic Metadata Extractor** - Priority 4 (Fallback)
- **Status**: ⏳ Pending
- **Complexity**: ⭐ (Low - struct-based flexibility)
- **Verified Tika Interfaces**:
  - ✅ `PST.java` - PST file properties
  - ✅ `QuattroPro.java` - QuattroPro spreadsheet
  - ✅ `WordPerfect.java` - WordPerfect document
  - ✅ `MachineMetadata.java` - Machine metadata
  - ✅ `ExternalProcess.java` - External process metadata
  - ✅ `Geographic.java` - Geographic data
  - ✅ `FileSystem.java` - File system metadata
  - ✅ `HttpHeaders.java` - HTTP headers
  - ✅ `Rendering.java` - Rendering properties
  - ✅ `PagedText.java` - Paged text properties
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.generic.v1.GenericMetadata`
- **Implementation**: Create `GenericMetadataBuilder.java`
- **Special Notes**: **ULTIMATE FALLBACK** - handles any document type through struct flexibility

---

## 🔧 Implementation Guidelines

### ⚠️ CRITICAL RULES
1. **ONLY use properties that actually exist** in the verified Tika interfaces
2. **Check TIKA_INTERFACE_MAPPING.md** before implementing any builder
3. **Use proper Property constants** - don't use string literals
4. **Handle multiple interfaces** - some document types have multiple related interfaces
5. **Fallback to struct** - anything not mapped goes to the flexible struct
6. **Follow the principle**: "Whatever Tika extracts, we save - strongly-typed if we recognize it, struct if we don't"

### Standard Builder Pattern
```java
public class {Type}MetadataBuilder {
    
    public static {Type}Metadata build(Metadata tikaMetadata, String parserClass, String tikaVersion) {
        {Type}Metadata.Builder builder = {Type}Metadata.newBuilder();
        Set<String> mappedFields = new HashSet<>();
        
        // 1. Map ONLY verified strongly-typed fields from Tika interfaces
        mapVerifiedFields(tikaMetadata, builder, mappedFields);
        
        // 2. Build additional metadata struct for unmapped fields
        Struct additionalMetadata = MetadataUtils.buildAdditionalMetadata(tikaMetadata, mappedFields);
        builder.setAdditionalMetadata(additionalMetadata);
        
        // 3. Build base fields
        TikaBaseFields baseFields = MetadataUtils.buildBaseFields(parserClass, tikaVersion, tikaMetadata);
        builder.setBaseFields(baseFields);
        
        return builder.build();
    }
    
    private static void mapVerifiedFields(Metadata tikaMetadata, 
                                        {Type}Metadata.Builder builder, 
                                        Set<String> mappedFields) {
        // Map ONLY properties that exist in the verified Tika interface
        // See TIKA_INTERFACE_MAPPING.md for the complete verified list
        MetadataUtils.mapStringField(tikaMetadata, VERIFIED_INTERFACE.VERIFIED_FIELD, builder::setVerifiedField, mappedFields);
        // ... continue ONLY for verified interface fields
    }
}
```

---

## 🧪 Testing Strategy

### Backward Compatibility Testing
1. **Existing Tests**: All current tests should continue to pass
2. **Current API**: `parseDocument()` method unchanged
3. **Current Output**: `PipeDoc` structure unchanged

### New Functionality Testing
1. **Interface Verification**: Test each builder with verified Tika interface properties only
2. **Document Type Detection**: Verify correct routing
3. **Struct Population**: Validate unmapped metadata capture
4. **Integration**: Test new `parseDocumentWithMetadata()` method

---

## 📝 Session Instructions

### For Each Metadata Extractor Implementation:

1. **Consult TIKA_INTERFACE_MAPPING.md**: Check verified interfaces and properties

2. **Verify Protobuf Fields**: Ensure our protobuf has fields for the verified Tika properties

3. **Create Accurate Builder**:
   - Use **only** verified Tika interface properties
   - Follow standard builder pattern
   - Map verified fields to protobuf fields
   - Handle unmapped fields in struct

4. **Test Builder**:
   - Unit tests with verified Tika properties
   - Integration tests with sample files from `sample_doc_types/`

5. **Update Progress**:
   - Move from PENDING to COMPLETED
   - Update progress tracker

### Success Criteria Per Extractor:
- ✅ Uses only verified Tika interface properties
- ✅ Maps all verified properties to protobuf fields
- ✅ Struct contains unmapped metadata
- ✅ TikaBaseFields populated properly
- ✅ Unit tests pass
- ✅ Integration tests with sample files pass
- ✅ **Existing functionality unchanged**

---

**🚀 Ready to Start**: Begin with PDF Metadata Extractor using **verified** properties from TIKA_INTERFACE_MAPPING.md. Create accurate `PdfMetadataBuilder.java` that maps only the properties that actually exist in PDF.java interface, while keeping all existing DocumentParser functionality intact.

#### 2. **Office Metadata Extractor** - Priority 1  
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - multiple interfaces)
- **Source Entity**:
  - Multiple Tika Interfaces: `Office.java`, `MSOffice.java`, `OfficeOpenXMLCore.java`, `OfficeOpenXMLExtended.java`
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.office.v1.OfficeMetadata` (89 fields)
- **Implementation**: Create `OfficeMetadataBuilder.java`

#### 3. **Image Metadata Extractor** - Priority 1
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐⭐ (Very High - multiple image formats + EXIF)
- **Source Entity**:
  - Multiple Tika Interfaces: `TIFF.java`, `JPEG.java`, `PNG.java`, `GIF.java`, `BMP.java`, `EXIF.java`, `IPTC.java`
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.image.v1.ImageMetadata` (156 fields)
- **Implementation**: Create `ImageMetadataBuilder.java`

#### 4-14. **Remaining Extractors** - Priorities 2-4
- Email, Media, HTML, RTF, Database, Font, EPUB, WARC, ClimateForcast, CreativeCommons, Generic
- Same pattern: Create `{Type}MetadataBuilder.java` for each

---

## 🔧 Implementation Guidelines

### Standard Builder Pattern
```java
public class {Type}MetadataBuilder {
    
    public static {Type}Metadata build(Metadata tikaMetadata) {
        {Type}Metadata.Builder builder = {Type}Metadata.newBuilder();
        Set<String> mappedFields = new HashSet<>();
        
        // 1. Map strongly-typed fields from Tika interfaces
        mapStronglyTypedFields(tikaMetadata, builder, mappedFields);
        
        // 2. Build additional metadata struct for unmapped fields
        Struct additionalMetadata = buildAdditionalMetadata(tikaMetadata, mappedFields);
        builder.setAdditionalMetadata(additionalMetadata);
        
        // 3. Build base fields
        TikaBaseFields baseFields = buildBaseFields(parserClass, tikaVersion, tikaMetadata);
        builder.setBaseFields(baseFields);
        
        return builder.build();
    }
    
    private static void mapStronglyTypedFields(Metadata tikaMetadata, 
                                             {Type}Metadata.Builder builder, 
                                             Set<String> mappedFields) {
        // Map all interface fields to protobuf fields
        mapStringField(tikaMetadata, INTERFACE.FIELD1, builder::setField1, mappedFields);
        mapIntField(tikaMetadata, INTERFACE.FIELD2, builder::setField2, mappedFields);
        // ... continue for all interface fields
    }
}
```

### Integration with Current Service
```java
// In ParserServiceImpl.java - ADD this alongside existing functionality
public class ParserServiceImpl {
    
    // KEEP existing processDocument method unchanged
    
    // ADD new method for comprehensive metadata (optional enhancement)
    public Uni<TikaResponse> extractComprehensiveMetadata(ModuleProcessRequest request) {
        // Extract blob data (reuse existing logic)
        // Call DocumentParser.parseDocumentWithMetadata()
        // Return TikaResponse with comprehensive metadata
    }
}
```

---

## 🧪 Testing Strategy

### Backward Compatibility Testing
1. **Existing Tests**: All current tests should continue to pass
2. **Current API**: `parseDocument()` method unchanged
3. **Current Output**: `PipeDoc` structure unchanged

### New Functionality Testing
1. **Metadata Extraction**: Test each builder with sample files
2. **Document Type Detection**: Verify correct routing
3. **Struct Population**: Validate unmapped metadata capture
4. **Integration**: Test new `parseDocumentWithMetadata()` method

---

## 📝 Session Instructions

### For Each Metadata Extractor Implementation:

1. **Choose Next Extractor**: Start with PDF (Priority 1, well-defined)

2. **Create Builder Class**:
   - Create `{Type}MetadataBuilder.java` in `ai.pipestream.module.parser.tika.builders`
   - Follow standard builder pattern
   - Map all Tika interface fields to protobuf fields

3. **Test Builder**:
   - Unit tests with mock Tika metadata
   - Integration tests with sample files from `sample_doc_types/`

4. **Integrate with System**:
   - Add to `TikaMetadataExtractor`
   - Test document type detection
   - Validate end-to-end flow

5. **Update Progress**:
   - Move from PENDING to COMPLETED
   - Update progress tracker

### Success Criteria Per Extractor:
- ✅ All Tika interface fields mapped to protobuf
- ✅ Struct contains unmapped metadata
- ✅ TikaBaseFields populated properly
- ✅ Unit tests pass
- ✅ Integration tests with sample files pass
- ✅ **Existing functionality unchanged**

---

**🚀 Ready to Start**: Begin with PDF Metadata Extractor - create `PdfMetadataBuilder.java` that maps all 87 PDF interface fields to `PdfMetadata` protobuf structure, while keeping all existing DocumentParser functionality intact.

#### 2. **Office Parser** - Priority 1  
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - multiple interfaces)
- **Source Entity**:
  - Tika Interface: `org.apache.tika.metadata.Office.java` (basic properties)
  - Tika Interface: `org.apache.tika.metadata.MSOffice.java` (MS-specific)
  - Tika Interface: `org.apache.tika.metadata.OfficeOpenXMLCore.java` (OOXML core)
  - Tika Interface: `org.apache.tika.metadata.OfficeOpenXMLExtended.java` (OOXML extended)
  - Parser Classes: POI-based parsers (Word, Excel, PowerPoint)
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.office.v1.OfficeMetadata` (89 fields)
  - Package: `io.pipeline.parsed.data.office.v1`
- **Key Mapping Logic**:
  ```java
  // Multiple interface mappings
  Office.CHARACTER_COUNT → character_count (int64)
  MSOffice.APPLICATION_NAME → application_name (string)
  OfficeOpenXMLCore.CREATOR → creator (string)
  OfficeOpenXMLExtended.APPLICATION → application (string)
  // ... combine all Office interface fields
  ```
- **Implementation File**: `OfficeMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/office/`

#### 3. **Image Parser** - Priority 1
- **Status**: ⏳ Pending  
- **Complexity**: ⭐⭐⭐⭐ (Very High - multiple image formats + EXIF)
- **Source Entity**:
  - Tika Interfaces: `TIFF.java`, `JPEG.java`, `PNG.java`, `GIF.java`, `BMP.java`
  - Tika Interfaces: `EXIF.java`, `IPTC.java` (metadata standards)
  - Parser Classes: ImageIO and metadata-extractor based
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.image.v1.ImageMetadata` (156 fields)
  - Package: `io.pipeline.parsed.data.image.v1`
- **Key Mapping Logic**:
  ```java
  // Format-specific mappings
  TIFF.IMAGE_WIDTH → image_width (int32)
  JPEG.COMPRESSION_TYPE → compression_type (string)
  EXIF.GPS_LATITUDE → gps_latitude (double)
  IPTC.KEYWORDS → iptc_keywords (repeated string)
  // ... handle all image format interfaces
  ```
- **Implementation File**: `ImageMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/image/`

#### 4. **Email Parser** - Priority 2
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - email headers + attachments)
- **Source Entity**:
  - Tika Interface: `org.apache.tika.metadata.Message.java`
  - Tika Interface: `org.apache.tika.metadata.MSOutlook.java`
  - Parser Classes: JavaMail and POI-based parsers
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.email.v1.EmailMetadata` (89 fields)
  - Package: `io.pipeline.parsed.data.email.v1`
- **Key Mapping Logic**:
  ```java
  // Email header mappings
  Message.MESSAGE_FROM → from_address (string)
  Message.MESSAGE_TO → to_addresses (repeated string)
  Message.MESSAGE_SUBJECT → subject (string)
  MSOutlook.MAPI_MESSAGE_CLASS → message_class (string)
  // ... handle email-specific metadata
  ```
- **Implementation File**: `EmailMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/email/`

#### 5. **Media Parser** - Priority 2
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - multiple media formats)
- **Source Entity**:
  - Tika Interfaces: `XMPDM.java`, `MP4.java`, `QuickTime.java`, `FLAC.java`, `MP3.java`
  - Parser Classes: Various media format parsers
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.media.v1.MediaMetadata` (87 fields)
  - Package: `io.pipeline.parsed.data.media.v1`
- **Key Mapping Logic**:
  ```java
  // Media format mappings
  XMPDM.DURATION → duration_seconds (double)
  MP4.CREATION_TIME → creation_time (timestamp)
  MP3.BITRATE → bitrate (int32)
  FLAC.BITS_PER_SAMPLE → bits_per_sample (int32)
  // ... handle media-specific metadata
  ```
- **Implementation File**: `MediaMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/media/`

#### 6. **HTML Parser** - Priority 2
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐ (Medium - web metadata)
- **Source Entity**:
  - Tika Interface: `org.apache.tika.metadata.HTML.java`
  - Parser Class: HTML parser with meta tag extraction
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.html.v1.HtmlMetadata` (75 fields)
  - Package: `io.pipeline.parsed.data.html.v1`
- **Key Mapping Logic**:
  ```java
  // HTML meta tag mappings
  HTML.DESCRIPTION → description (string)
  HTML.KEYWORDS → keywords (repeated string)
  // OpenGraph properties
  "og:title" → og_title (string)
  "og:description" → og_description (string)
  // ... handle web-specific metadata
  ```
- **Implementation File**: `HtmlMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/html/`

#### 7. **EPUB Parser** - Priority 2 (Critical for Chunking!)
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐⭐ (Very High - structural metadata)
- **Source Entity**:
  - Tika Interface: `org.apache.tika.metadata.Epub.java` (minimal - 2 properties)
  - Parser Classes: EpubParser, OPFParser, DcXMLParser
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.epub.v1.EpubMetadata` (100 fields)
  - Package: `io.pipeline.parsed.data.epub.v1`
- **Key Mapping Logic**:
  ```java
  // EPUB-specific mappings
  Epub.RENDITION_LAYOUT → rendition_layout (string)
  Epub.VERSION → version (string)
  
  // Complex structural parsing needed:
  // - Parse OPF file for spine_items, manifest_items
  // - Extract TOC from NCX or Navigation Document
  // - Analyze embedded resources (images, fonts, CSS)
  // - Calculate word/character counts for chunking
  ```
- **Implementation File**: `EpubMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/epub/`
- **Special Notes**: **CRITICAL FOR CHUNKING** - spine_items and table_of_contents provide natural chunk boundaries

#### 8. **WARC Parser** - Priority 2 (Important for Preservarca!)
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐⭐ (Very High - web archive complexity)
- **Source Entity**:
  - Tika Interface: `org.apache.tika.metadata.WARC.java` (4 properties, marked "TODO: lots")
  - Parser Class: WARCParser using jwarc library
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.warc.v1.WarcMetadata` (157 fields)
  - Package: `io.pipeline.parsed.data.warc.v1`
- **Key Mapping Logic**:
  ```java
  // WARC header mappings
  WARC.WARC_RECORD_ID → warc_record_id (string)
  WARC.WARC_RECORD_CONTENT_TYPE → warc_record_content_type (string)
  
  // Complex WARC parsing needed:
  // - Extract all WARC-* headers from record
  // - Parse HTTP response metadata (status, headers)
  // - Handle redirect chains and cookies
  // - Extract crawl and preservation metadata
  ```
- **Implementation File**: `WarcMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/warc/`
- **Special Notes**: **IMPORTANT FOR PRESERVARCA** - comprehensive web archive metadata

#### 9. **RTF Parser** - Priority 3
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐ (Medium - RTF format)
- **Source Entity**:
  - Tika Interface: `org.apache.tika.metadata.RTF.java`
  - Tika Interface: `org.apache.tika.metadata.MSOffice.java` (shared)
  - Parser Class: RTF parser
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.rtf.v1.RtfMetadata` (58 fields)
  - Package: `io.pipeline.parsed.data.rtf.v1`
- **Key Mapping Logic**:
  ```java
  // RTF-specific mappings
  RTF.AUTHOR → author (string)
  RTF.CREATION_DATE → creation_date (timestamp)
  MSOffice.APPLICATION_NAME → application_name (string)
  // ... handle RTF format metadata
  ```
- **Implementation File**: `RtfMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/rtf/`

#### 10. **Database Parser** - Priority 3
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - database schema)
- **Source Entity**:
  - Tika Interface: `org.apache.tika.metadata.Database.java`
  - Tika Interface: `org.apache.tika.metadata.Access.java`
  - Parser Classes: Database format parsers (Access, etc.)
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.database.v1.DatabaseMetadata` (89 fields)
  - Package: `io.pipeline.parsed.data.database.v1`
- **Key Mapping Logic**:
  ```java
  // Database metadata mappings
  Database.TABLE_COUNT → table_count (int32)
  Access.DATABASE_VERSION → database_version (string)
  // ... handle database schema metadata
  ```
- **Implementation File**: `DatabaseMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/database/`

#### 11. **Font Parser** - Priority 3
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐ (Medium - font metrics)
- **Source Entity**:
  - Tika Interface: `org.apache.tika.metadata.Font.java` (minimal - 1 property)
  - Parser Classes: AdobeFontMetricParser, TrueTypeParser
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.tika.font.v1.FontMetadata` (58 fields)
  - Package: `io.pipeline.parsed.data.tika.font.v1`
- **Key Mapping Logic**:
  ```java
  // Font metadata mappings
  Font.FONT_NAME → font_name (string)
  
  // Parser-specific extraction needed:
  // - FontBox NameRecord constants for TrueType
  // - Adobe Font Metric constants for AFM files
  // - Font technical properties and metrics
  ```
- **Implementation File**: `FontMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/font/`

#### 12. **ClimateForcast Parser** - Priority 3
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐⭐ (Very High - scientific data)
- **Source Entity**:
  - Tika Interface: `org.apache.tika.metadata.ClimateForcast.java` (16 CF Convention properties)
  - Parser Class: NetCDFParser using UCAR NetCDF-Java library
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.climate.v1.ClimateForcastMetadata` (130 fields)
  - Package: `io.pipeline.parsed.data.climate.v1`
- **Key Mapping Logic**:
  ```java
  // CF Convention mappings
  ClimateForcast.INSTITUTION → institution (string)
  ClimateForcast.EXPERIMENT_ID → experiment_id (string)
  
  // Complex NetCDF parsing needed:
  // - Extract dimensions, variables, attributes
  // - Parse coordinate systems and projections
  // - Handle scientific metadata and quality info
  ```
- **Implementation File**: `ClimateForcastMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/climate_forecast/`

#### 13. **CreativeCommons Parser** - Priority 4
- **Status**: ⏳ Pending
- **Complexity**: ⭐⭐⭐ (High - rights management)
- **Source Entity**:
  - Tika Interface: `org.apache.tika.metadata.CreativeCommons.java` (3 properties)
  - Tika Interface: `org.apache.tika.metadata.XMPRights.java` (5 properties)
  - Parser Classes: XMP parsers, HTML parsers, various metadata extractors
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.creative_commons.v1.CreativeCommonsMetadata` (60 fields)
  - Package: `io.pipeline.parsed.data.creative_commons.v1`
- **Key Mapping Logic**:
  ```java
  // Creative Commons mappings
  CreativeCommons.LICENSE_URL → license_url (string)
  CreativeCommons.WORK_TYPE → work_type (string)
  XMPRights.USAGE_TERMS → usage_terms (string)
  
  // Complex rights parsing needed:
  // - Detect CC license types from URLs/metadata
  // - Extract attribution requirements
  // - Parse usage permissions and restrictions
  ```
- **Implementation File**: `CreativeCommonsMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/creative_commons/`

#### 14. **Generic Parser** - Priority 4 (Fallback)
- **Status**: ⏳ Pending
- **Complexity**: ⭐ (Low - struct-based flexibility)
- **Source Entity**:
  - No specific Tika interface (handles unknown formats)
  - Parser Classes: Various parsers for uncommon formats
- **Destination Entity**:
  - Protobuf: `io.pipeline.parsed.data.generic.v1.GenericMetadata` (25 fields)
  - Package: `io.pipeline.parsed.data.generic.v1`
- **Key Mapping Logic**:
  ```java
  // Basic identification
  detected_mime_type → detected_mime_type (string)
  tika_parser_class → tika_parser_class (string)
  
  // Maximum flexibility:
  // - All Tika metadata goes into all_metadata struct
  // - Basic document properties extracted where possible
  // - Fallback for any unknown document type
  ```
- **Implementation File**: `GenericMetadataBuilder.java`
- **Test Files**: `src/test/resources/sample_doc_types/generic/`
- **Special Notes**: **ULTIMATE FALLBACK** - handles any document type through struct flexibility

---

## 🔧 Implementation Guidelines

### Standard Builder Pattern
Each metadata builder follows this pattern:

```java
public class PdfMetadataBuilder {
    
    public static PdfMetadata.Builder buildPdfMetadata(
            Metadata tikaMetadata, 
            String parserClass, 
            String tikaVersion) {
        
        PdfMetadata.Builder builder = PdfMetadata.newBuilder();
        Set<String> mappedFields = new HashSet<>();
        
        // 1. Map strongly-typed fields
        mapStronglyTypedFields(tikaMetadata, builder, mappedFields);
        
        // 2. Build additional metadata struct
        Struct additionalMetadata = buildAdditionalMetadata(tikaMetadata, mappedFields);
        builder.setAdditionalMetadata(additionalMetadata);
        
        // 3. Build base fields
        TikaBaseFields baseFields = buildBaseFields(parserClass, tikaVersion, tikaMetadata);
        builder.setBaseFields(baseFields);
        
        return builder;
    }
    
    private static void mapStronglyTypedFields(Metadata tikaMetadata, 
                                             PdfMetadata.Builder builder, 
                                             Set<String> mappedFields) {
        // Direct field mappings from Tika interfaces
        mapStringField(tikaMetadata, PDF.PDF_VERSION, builder::setPdfVersion, mappedFields);
        mapStringField(tikaMetadata, PDF.PRODUCER, builder::setProducer, mappedFields);
        mapBooleanField(tikaMetadata, PDF.ENCRYPTED, builder::setIsEncrypted, mappedFields);
        mapIntField(tikaMetadata, PDF.PAGE_COUNT, builder::setPageCount, mappedFields);
        // ... continue for all PDF interface fields
    }
}
```

### Utility Methods
Common utility methods for all builders:

```java
// Type conversion utilities
public static void mapStringField(Metadata metadata, String key, Consumer<String> setter, Set<String> mapped)
public static void mapIntField(Metadata metadata, String key, Consumer<Integer> setter, Set<String> mapped)  
public static void mapBooleanField(Metadata metadata, String key, Consumer<Boolean> setter, Set<String> mapped)
public static void mapTimestampField(Metadata metadata, String key, Consumer<Timestamp> setter, Set<String> mapped)

// Struct building utilities
public static Struct buildAdditionalMetadata(Metadata tikaMetadata, Set<String> mappedFields)
public static TikaBaseFields buildBaseFields(String parserClass, String tikaVersion, Metadata tikaMetadata)
```

### Document Type Detection
Central router to determine which builder to use:

```java
public class DocumentTypeDetector {
    
    public static DocumentType detectDocumentType(String mimeType, Metadata tikaMetadata) {
        // MIME type based detection with fallbacks
        if (mimeType.startsWith("application/pdf")) return DocumentType.PDF;
        if (mimeType.contains("officedocument") || mimeType.contains("msword")) return DocumentType.OFFICE;
        if (mimeType.startsWith("image/")) return DocumentType.IMAGE;
        // ... continue for all types
        return DocumentType.GENERIC; // fallback
    }
}
```

---

## 🧪 Testing Strategy

### Per Parser Testing
1. **Unit Tests**: Test builder with known Tika metadata
2. **Integration Tests**: Test with real sample files
3. **Validation Tests**: Verify protobuf structure correctness
4. **Performance Tests**: Ensure acceptable parse times

### Overall System Testing
1. **Document Type Detection**: Verify correct routing
2. **Metadata Completeness**: Ensure no data loss
3. **Struct Population**: Validate flexible metadata capture
4. **JSON Serialization**: Test output format

---

## 📝 Session Instructions

### For Each Parser Implementation Session:

1. **Choose Next Parser**: Start with Priority 1 (PDF, Office, Image)

2. **Analyze Source**: 
   - Review Tika interface(s) and constants
   - Examine parser implementation
   - Understand metadata extraction patterns

3. **Create Builder Class**:
   - Follow standard builder pattern
   - Map all interface fields to protobuf fields
   - Handle type conversions properly
   - Populate struct with unmapped metadata

4. **Test Implementation**:
   - Create unit tests with mock metadata
   - Test with real sample files
   - Validate protobuf output

5. **Update Progress**:
   - Move parser from PENDING to COMPLETED
   - Update progress tracker
   - Document any issues or special cases

### Success Criteria Per Parser:
- ✅ All Tika interface fields mapped to protobuf
- ✅ Type conversions work correctly  
- ✅ Struct contains unmapped metadata
- ✅ TikaBaseFields populated properly
- ✅ Unit tests pass
- ✅ Integration tests with sample files pass
- ✅ JSON serialization works

---

**🚀 Ready to Start**: Begin with PDF Parser (Priority 1, Medium complexity) - well-defined interface with 87 fields to map from PDF.java and XMPPDF.java interfaces to PdfMetadata protobuf structure.
