# Docling Integration Plan - module-parser

## Critical Architecture (DO NOT FORGET)

**Two parsers run CONCURRENTLY in module-parser:**
1. **TikaParser** (existing) → TikaResponse proto → stored in `parsed_metadata["tika"]`
2. **DoclingParser** (new) → DoclingResponse proto → stored in `parsed_metadata["docling"]`

**Location:** ALL parsing in `module-parser` (NOT pipestream-server)

**Proto-First:** NO POJOs - use proto classes directly

**Dependencies already in place:**
- ✅ `quarkus-docling:1.2.2` in build.gradle line 45
- ✅ Proto toolchain configured (lines 30-42)
- ✅ Proto classes generated from `pipestream-protos`

## Proto Structure (Already Created - 1:1 with Docling)

### Common Module
- **docling_document.proto** (1,050 lines) - Complete 1:1 mapping with Docling Java
- **docling_response.proto** (471 lines) - Wrapper with status, metadata, exports
- **docling_parser.proto** (115 lines) - Parser interface

### Mapping: Docling Java API → Proto

```
ConvertDocumentResponse (from DoclingService)
├── DocumentResponse.jsonContent (DoclingDocument Java)
│   └── Convert to DoclingResponse.document via JSON
├── DocumentResponse.markdownContent → DoclingResponse.markdown
├── DocumentResponse.htmlContent → DoclingResponse.html
├── DocumentResponse.textContent → DoclingResponse.text
├── status → DoclingParseStatus.status
├── processingTime → DoclingParseStatus.parse_time_ms
└── errors → DoclingParseStatus.errors
```

## Implementation Steps

### 1. Create DoclingMetadataExtractor
**Path:** `module-parser/src/main/java/ai/pipestream/module/parser/docling/DoclingMetadataExtractor.java`

```java
@ApplicationScoped
public class DoclingMetadataExtractor {
    @Inject DoclingService doclingService;

    public DoclingResponse extractComprehensiveMetadata(
        byte[] content, String filename, String docId
    ) throws Exception {
        // Call doclingService.convertFromBytes()
        // Map ConvertDocumentResponse → DoclingResponse proto
        // Use Jackson ObjectMapper + JsonFormat for DoclingDocument conversion
    }
}
```

### 2. Update ParserServiceImpl
**Path:** `module-parser/src/main/java/ai/pipestream/module/parser/ParserServiceImpl.java`

**Current (line 124):** Tika only
```java
parsedDoc = documentParser.parseDocument(blobData, config, filename);
```

**New:** Concurrent Tika + Docling
```java
// Run both parsers concurrently
Uni<PipeDoc> tikaUni = Uni.createFrom().item(() ->
    documentParser.parseDocument(blobData, config, filename)
);

Uni<DoclingResponse> doclingUni = Uni.createFrom().item(() ->
    doclingMetadataExtractor.extractComprehensiveMetadata(
        blobData.toByteArray(), filename, docId
    )
).onFailure().recoverWithNull(); // Graceful degradation

// Combine results
return Uni.combine().all().unis(tikaUni, doclingUni).asTuple()
    .map(tuple -> {
        // Store TikaResponse in parsed_metadata["tika"]
        // Store DoclingResponse in parsed_metadata["docling"]
    });
```

### 3. Key Implementation Details

**JSON Conversion (DoclingDocument Java → Proto):**
```java
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(doclingDocument);
DoclingDocument.Builder builder = DoclingDocument.newBuilder();
JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
DoclingDocument proto = builder.build();
```

**Status Mapping:**
```java
"success" → DoclingParseStatus.Status.STATUS_SUCCESS
"partial_success" → DoclingParseStatus.Status.STATUS_PARTIAL
"failure" → DoclingParseStatus.Status.STATUS_FAILED
"timeout" → DoclingParseStatus.Status.STATUS_TIMEOUT
```

## Result Structure

```protobuf
PipeDoc {
  doc_id: "doc-123"
  search_metadata: { ... }  // From Tika
  parsed_metadata: {
    "tika": ParsedMetadata {
      parser_name: "tika"
      data: Any(TikaResponse)  // Dublin Core, PDF metadata
    }
    "docling": ParsedMetadata {
      parser_name: "docling"
      data: Any(DoclingResponse)  // Structure, tables, forms
    }
  }
}
```

## Benefits

**Tika provides:** Dublin Core, PDF/Office metadata, XMP, basic text
**Docling provides:** Document structure, tables, AI image classification, forms, layout

**Concurrent:** Both run in parallel - Docling failure doesn't break Tika
**DevServices:** Container auto-starts in dev/test mode at localhost:5001

## Configuration

```properties
# Dev/Test (automatic)
quarkus.docling.devservices.enabled=true
quarkus.docling.devservices.image=ghcr.io/docling-project/docling-serve:v1.10.0

# Production
quarkus.docling.base-url=http://docling-serve:5001
quarkus.docling.devservices.enabled=false
```

## Implementation Status

✅ **Step 1: DoclingMetadataExtractor** - COMPLETED
- Created in `module-parser/src/main/java/ai/pipestream/module/parser/docling/DoclingMetadataExtractor.java`
- Calls DoclingService.convertFromBytes()
- Maps ConvertDocumentResponse → DoclingResponse proto via JSON conversion
- Handles errors gracefully with proper status mapping
- Stores markdown, html, and text exports

✅ **Step 2: ParserServiceImpl Updates** - COMPLETED
- Added injection of DoclingMetadataExtractor
- Implemented concurrent execution using Uni.combine().all().unis()
- Both Tika and Docling run in parallel with graceful degradation
- TikaResponse stored in parsed_metadata["tika"]
- DoclingResponse stored in parsed_metadata["docling"]
- Added helper methods: storeTikaMetadata() and storeDoclingMetadata()
- Added shouldExtractDoclingMetadata() configuration method

## Testing Needed

- Unit tests for DoclingMetadataExtractor
- Integration tests with actual document parsing
- Verify concurrent execution performance
- Test graceful degradation when Docling service is unavailable
