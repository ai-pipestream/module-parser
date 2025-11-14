# Module Parser

A high-performance document parsing microservice for the [Pipestream AI](https://github.com/ai-pipestream) platform, built on Apache Tika and Quarkus.

## Overview

The **module-parser** is a critical pipeline component in the Pipestream AI ecosystem that transforms raw documents into structured, searchable data. It extracts text, metadata, and document structure from 100+ file formats, enabling downstream AI/ML processing, search indexing, and content analysis.

### Role in Pipestream AI

Pipestream AI is a distributed document processing platform where specialized microservices collaborate through gRPC to handle complex workflows. The parser module serves as the **ingestion gateway**, responsible for:

- **Document Understanding**: Converting diverse file formats into standardized representations
- **Metadata Enrichment**: Extracting 1,330+ metadata fields across 14 document types
- **Structure Extraction**: Building document outlines, link graphs, and hierarchies
- **Pipeline Integration**: Producing `PipeDoc` messages for downstream processing modules
- **Quality Assurance**: Validating document quality and handling parsing errors gracefully

Each parsed document flows through the platform's service mesh, where it can be indexed for search, analyzed by AI models, or transformed by other pipeline modules—all coordinated through the platform's dynamic service discovery and gRPC communication layer.

## Built on Apache Tika

At its core, this service leverages [Apache Tika](https://tika.apache.org/), the industry-standard content analysis toolkit. Tika provides:

- **Universal Format Support**: Parsers for documents, images, audio, video, archives, and more
- **Metadata Extraction**: Access to embedded metadata across formats (EXIF, XMP, Office properties)
- **Content Detection**: Automatic MIME type detection and character encoding handling
- **Text Extraction**: Unified API for extracting plain text from binary formats

The module-parser builds upon Tika's foundation by:

- Wrapping Tika's capabilities in modern gRPC and REST APIs
- Mapping Tika's loosely-typed metadata to strongly-typed Protocol Buffers
- Adding specialized extractors for PDF outlines, EPUB structure, and HTML hierarchies
- Providing enterprise features like error handling, configuration management, and service discovery
- Offering developer-friendly testing endpoints and comprehensive documentation

### Tika Version & Parsers

- **Tika Core**: 3.2.1
- **Standard Parsers**: Microsoft Office, PDF, OpenDocument, RTF, images, email, and more
- **Scientific Parsers**: NetCDF climate data, specialized scientific formats
- **OCR Support**: Tesseract integration for scanned documents and images

## Extensive Protocol Buffers

The parser produces strongly-typed metadata using an extensive set of Protocol Buffer definitions maintained in the [platform-libraries repository](https://github.com/ai-pipestream/platform-libraries/tree/main/grpc/grpc-stubs/src/main/proto/module/parser/tika).

### Protobuf Schema Overview

**17 protobuf files** define comprehensive metadata structures:

#### Document-Specific Metadata
- [`pdf_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/pdf_metadata.proto) - 50+ PDF fields (version, encryption, permissions, producer, etc.)
- [`office_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/office_metadata.proto) - Microsoft Office & OpenOffice properties
- [`image_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/image_metadata.proto) - TIFF/EXIF/IPTC metadata, camera settings, GPS coordinates
- [`email_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/email_metadata.proto) - Email headers, attachments, MAPI properties
- [`media_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/media_metadata.proto) - Audio/video XMP metadata
- [`html_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/html_metadata.proto) - Web document properties
- [`epub_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/epub_metadata.proto) - E-book format metadata
- [`rtf_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/rtf_metadata.proto) - Rich Text Format properties
- [`font_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/font_metadata.proto) - Font file attributes (TTF, OTF, WOFF)
- [`database_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/database_metadata.proto) - Database schema information
- [`warc_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/warc_metadata.proto) - Web archive format data
- [`climate_forecast_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/climate_forecast_metadata.proto) - NetCDF climate data

#### Standard Metadata Frameworks
- [`dublin_core.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/dublin_core.proto) - Dublin Core metadata standard
- [`creative_commons_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/creative_commons_metadata.proto) - Creative Commons licensing

#### Core Infrastructure
- [`tika_response.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/tika_response.proto) - Top-level response structure
- [`tika_base_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/tika_base_metadata.proto) - Foundation metadata types
- [`generic_metadata.proto`](https://github.com/ai-pipestream/platform-libraries/blob/main/grpc/grpc-stubs/src/main/proto/module/parser/tika/generic_metadata.proto) - Universal metadata fields

This comprehensive schema enables **strongly-typed metadata extraction** with over **1,330 fields** mapped from Tika's interfaces to structured protobuf messages.

## Key Features

### Comprehensive Metadata Extraction
- **1,330+ metadata fields** across 14 document types
- **18 specialized metadata builders** for format-specific extraction
- Automatic mapping from Tika metadata interfaces to structured protobufs
- XMP metadata extraction (Rights, PDF, Digital Media)
- Dublin Core standard metadata support
- Access permissions and security metadata

### Document Structure Extraction
- **PDF Outlines**: Bookmark hierarchy extraction via PDFBox
- **EPUB Table of Contents**: Complete e-book navigation structure
- **HTML Outlines**: Heading hierarchy (H1-H6) with CSS selector support
- **Markdown Structure**: Heading extraction with CommonMark parser

### Link Discovery
- HTML link extraction with external/internal classification
- Markdown hyperlink discovery
- Link context and metadata tracking

### Multi-Format Support
100+ file formats including:
- **Documents**: PDF, Word (.doc/.docx), PowerPoint, Excel, RTF, OpenDocument
- **Images**: JPEG, PNG, TIFF, GIF with full EXIF/IPTC metadata
- **Emails**: EML, MSG, MBOX with attachment metadata
- **E-books**: EPUB with table of contents
- **Archives**: WARC web archives
- **Media**: Audio/video with XMP metadata
- **Data**: Databases, NetCDF climate files
- **Web**: HTML, XML with link extraction
- **Fonts**: TTF, OTF, WOFF with font metrics

### Configuration Management
- **Pre-built configurations** for common scenarios:
    - Default parsing with balanced settings
    - Large document processing (optimized memory)
    - Fast processing (speed-optimized)
    - Batch processing (resilient error handling)
    - Strict quality control (fail-fast validation)
- **Rich configuration options**:
    - Content length limits
    - Metadata extraction controls
    - Timeout management
    - Geo-parser and EMF parser toggles
    - MIME type filtering
    - Outline extraction settings

### Developer-Friendly APIs
- **gRPC API**: High-performance binary protocol for production
- **REST API**: HTTP endpoints for testing and exploration
- **OpenAPI/Swagger**: Interactive API documentation at `/swagger-ui`
- **Pre-built examples**: Configuration templates for common use cases

### Enterprise Features
- **Service Discovery**: Automatic Consul registration
- **Health Checks**: Built-in health monitoring
- **Error Handling**: Graceful fallbacks and detailed error reporting
- **Schema Validation**: Configuration validation endpoints
- **MIME Type Detection**: Automatic content type detection

## Architecture

### Technology Stack
- **Runtime**: Quarkus 3.x (Java 21)
- **Build System**: Gradle with version catalogs
- **Parsing Engine**: Apache Tika 3.2.1
- **Protocols**: gRPC + REST (JAX-RS)
- **Service Discovery**: Consul via Smallrye Stork
- **Data Format**: Protocol Buffers (protobuf)
- **Additional Libraries**: PDFBox, CommonMark, Jackson

### Core Components

#### 1. ParserServiceImpl
The main gRPC service implementation (`ai.pipestream.module.parser.service.ParserServiceImpl`):
- Receives `ModuleProcessRequest` with document blobs
- Orchestrates parsing through `DocumentParser`
- Extracts metadata via `TikaMetadataExtractor`
- Builds structured `TikaResponse` with format-specific metadata
- Returns `ModuleProcessResponse` containing `PipeDoc`

#### 2. REST API (ParserServiceEndpoint)
Developer-friendly HTTP endpoints at `/api/parser/service/`:
- `/config` - Get parser configuration JSON schema
- `/health` - Service health check
- `/test` - Quick parser testing
- `/simple-form` - Form-based document upload
- `/parse-json` - JSON-based parsing with configuration
- `/parse-file` - Direct file upload
- `/config/validate` - Configuration validation
- `/config/examples` - Pre-built configuration examples
- `/demo/*` - Demo document testing

#### 3. Document Parser
Core parsing engine (`ai.pipestream.module.parser.parser.DocumentParser`):
- Auto-detects document types via Tika's `AutoDetectParser`
- Applies custom Tika configurations
- Enforces content length limits
- Handles special cases (fonts, EPUBs, embedded documents)
- Maps Tika metadata to protobuf structures

#### 4. Metadata Extraction System
18 specialized metadata builders (3,081 lines total):
- `PdfMetadataBuilder` - PDF documents
- `OfficeMetadataBuilder` - Microsoft Office formats
- `ImageMetadataBuilder` - Images with EXIF/IPTC
- `EmailMetadataBuilder` - Email messages
- `MediaMetadataBuilder` - Audio/video files
- `HtmlMetadataBuilder` - HTML documents
- `RtfMetadataBuilder` - RTF documents
- `EpubMetadataBuilder` - EPUB e-books
- `DatabaseMetadataBuilder` - Database files
- `FontMetadataBuilder` - Font files
- `WarcMetadataBuilder` - Web archives
- `ClimateForecastMetadataBuilder` - Climate data
- `CreativeCommonsMetadataBuilder` - CC licensing
- Plus extraction utilities for outlines, links, and type detection

### Data Flow

```
┌─────────────────────┐
│  Input Document     │
│  (Binary Blob)      │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ ParserServiceImpl   │
│  (gRPC/REST)        │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  DocumentParser     │
│  (Apache Tika)      │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ TikaMetadataExtractor│
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ DocumentTypeDetector │
└──────────┬──────────┘
           │
           ├─→ PdfMetadataBuilder
           ├─→ OfficeMetadataBuilder
           ├─→ ImageMetadataBuilder
           ├─→ EmailMetadataBuilder
           └─→ [Other Builders...]
           │
           ▼
┌─────────────────────┐
│  TikaResponse       │
│  (Protobuf)         │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  PipeDoc            │
│  (Search Metadata   │
│   + Structured Data)│
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Downstream Pipeline │
│   (Search, AI/ML,   │
│    Analytics)       │
└─────────────────────┘
```

## Getting Started

### Prerequisites
- Java 21+
- Gradle 8.x
- Docker (optional, for containerized deployment)

### Quick Start

1. **Clone the repository**
```bash
git clone https://github.com/ai-pipestream/module-parser.git
cd module-parser
```

2. **Run in development mode**
```bash
./gradlew quarkusDev
```

The service will start on port `39001` with:
- gRPC endpoint: `localhost:39001`
- REST API: `http://localhost:39001/api/parser/service/`
- Swagger UI: `http://localhost:39001/swagger-ui/`

3. **Test with a sample document**
```bash
curl -F "file=@sample.pdf" http://localhost:39001/api/parser/service/simple-form
```

### Running Tests
```bash
./gradlew test
```

Tests use sample documents from Maven artifacts:
- `ai.pipestream.testdata:test-documents`
- `ai.pipestream.testdata:sample-doc-types`

## API Reference

### gRPC API

**Service**: `ai.pipestream.module.parser.service.ParserService`

**Method**: `Process`
- **Request**: `ModuleProcessRequest`
    - `config` - JSON string with `ParserConfig`
    - `document` - Document blob
    - `request_id` - Unique request identifier
- **Response**: `ModuleProcessResponse`
    - `pipe_doc` - Parsed document with metadata
    - `status` - Processing status
    - `error` - Error details (if applicable)

### REST API

Base path: `/api/parser/service/`

#### Configuration Endpoints

**GET** `/config`
- Returns JSON schema for `ParserConfig`
- Response: `application/json`

**POST** `/config/validate`
- Validates parser configuration
- Body: JSON configuration
- Response: Validation result

**GET** `/config/examples`
- Returns pre-built configuration examples
- Query params:
    - `scenario` - Configuration scenario name
- Response: List of example configurations

#### Parsing Endpoints

**POST** `/simple-form`
- Simple file upload parsing
- Body: `multipart/form-data`
    - `file` - Document file
- Response: Parsed text and metadata

**POST** `/parse-json`
- Parse with full configuration
- Body: JSON with `config` and base64-encoded `document`
- Response: Complete `TikaResponse`

**POST** `/parse-file`
- Direct file upload with config
- Body: `multipart/form-data`
    - `file` - Document file
    - `config` - JSON configuration (optional)
- Response: Complete `TikaResponse`

#### Utility Endpoints

**GET** `/health`
- Service health check
- Response: Health status

**GET** `/test`
- Test endpoint with demo document
- Response: Parsing result

**GET** `/demo/{filename}`
- Parse demo document by filename
- Path param: `filename` - Demo document name
- Response: Parsed content

## Configuration

The parser is configured using the `ParserConfig` record with multiple subsections:

### Parsing Options
```json
{
  "parsingOptions": {
    "maxContentLength": 1000000,
    "extractMetadata": true,
    "extractOutline": true,
    "extractLinks": true,
    "parseEmbeddedDocuments": true,
    "maxRecursionDepth": 10
  }
}
```

### Advanced Options
```json
{
  "advancedOptions": {
    "enableGeoParser": false,
    "disableEmfParser": true,
    "extractXmpRights": true
  }
}
```

### Content Type Handling
```json
{
  "contentTypeHandling": {
    "titleExtractionStrategy": "AUTO",
    "allowedMimeTypes": ["application/pdf", "text/html"],
    "blockedMimeTypes": []
  }
}
```

### Error Handling
```json
{
  "errorHandling": {
    "throwOnError": false,
    "fallbackStrategy": "BEST_EFFORT"
  }
}
```

### Pre-built Configurations

Access via `/config/examples?scenario=<name>`:

- **`default`** - Balanced settings for general use
- **`largeDocumentProcessing`** - Optimized for large files
- **`fastProcessing`** - Speed-optimized
- **`batchProcessing`** - Resilient batch operations
- **`strictQualityControl`** - Fail-fast validation

## Metadata Extraction Details

The parser extracts metadata through a sophisticated mapping system:

### Tika Interface Mapping
Tika metadata is organized into interfaces (e.g., `PDF`, `Office`, `TIFF`, `Message`). The parser maps each interface to corresponding protobuf messages.

See [TIKA_INTERFACE_MAPPING.md](TIKA_INTERFACE_MAPPING.md) for:
- Verified Tika metadata interfaces
- Interface-to-protobuf mappings
- Implementation guidelines

### Field-Level Mapping
Each Tika metadata field is mapped to specific protobuf fields with type conversion.

See [SOURCE_DESTINATION_MAPPING.md](SOURCE_DESTINATION_MAPPING.md) for:
- Exact field mappings (1,330+ fields)
- Property-by-property documentation
- Usage examples

### Actual Capabilities
Real-world Tika capabilities vary by document type.

See [tika-actual-metadata-fields.md](tika-actual-metadata-fields.md) for:
- What metadata is actually available
- Example outputs by document type
- Aspirational vs. real capabilities

## Development

### Project Structure
```
module-parser/
├── src/main/java/ai/pipestream/module/parser/
│   ├── service/          # gRPC and REST service implementations
│   ├── parser/           # Core parsing logic
│   ├── metadata/         # Metadata extraction system
│   │   ├── builders/     # Format-specific metadata builders
│   │   └── utils/        # Metadata utilities
│   ├── config/           # Configuration management
│   └── model/            # Domain models
├── src/test/java/        # Comprehensive test suite
├── docs/                 # Documentation
│   ├── TIKA_INTERFACE_MAPPING.md
│   ├── SOURCE_DESTINATION_MAPPING.md
│   └── tika-actual-metadata-fields.md
├── build.gradle          # Gradle build configuration
└── README.md             # This file
```

### Building

**Build the project**
```bash
./gradlew build
```

**Build Docker image**
```bash
./gradlew build -Dquarkus.container-image.build=true
```

**Run tests**
```bash
./gradlew test
```

### Code Quality

The codebase demonstrates excellent engineering practices:
- **Comprehensive documentation** (8.6 KB interface mapping, 19.9 KB field mapping)
- **Extensive testing** with format-specific integration tests
- **Clear separation of concerns** (service, parser, metadata layers)
- **Type safety** through Protocol Buffers
- **Error handling** with graceful degradation

### Development Mode

Run with live reload:
```bash
./gradlew quarkusDev
```

Features in dev mode:
- Live reload on code changes
- Swagger UI at `/swagger-ui/`
- Dev UI at `/q/dev/`
- Local Consul for service discovery

## Deployment

### Docker Deployment

Build and run with Docker:
```bash
./gradlew build -Dquarkus.container-image.build=true
docker run -p 39001:39001 ai.pipestream.module/module-parser:latest
```

### Kubernetes/OpenShift

The service is designed for cloud-native deployment:
- Health check endpoints for liveness/readiness probes
- Graceful shutdown support
- Externalized configuration via environment variables
- Service discovery integration

### Configuration Profiles

- **Production** (`prod`): Full service discovery, Consul registration
- **Development** (`dev`): Local Consul, compose dev services
- **Test** (`test`): Isolated testing without registration

### Resource Requirements

Recommended resources:
- **Memory**: 10GB heap (for large documents and Tika parsers)
- **CPU**: 2+ cores
- **Disk**: Minimal (stateless service)

Configure via JVM args:
```bash
-Xmx10g -XX:MaxMetaspaceSize=1g
```

## Service Registration

The parser automatically registers with the Pipestream platform:
- **Service Type**: `PARSER`
- **Capabilities**: Advertises supported MIME types
- **Schema**: Provides JSON schema for configuration
- **Health**: Reports health status to platform

Registration is handled by the `@GrpcServiceRegistration` annotation and platform libraries.

## Documentation

- [TIKA_INTERFACE_MAPPING.md](TIKA_INTERFACE_MAPPING.md) - Tika interface to protobuf mappings
- [SOURCE_DESTINATION_MAPPING.md](SOURCE_DESTINATION_MAPPING.md) - Field-level mapping documentation
- [tika-actual-metadata-fields.md](tika-actual-metadata-fields.md) - Real-world Tika capabilities
- [TODO.md](TODO.md) - Project roadmap and implementation tracking
- [Apache Tika Documentation](https://tika.apache.org/) - Upstream Tika docs
- [Pipestream Platform Libraries](https://github.com/ai-pipestream/platform-libraries) - Platform infrastructure

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

MIT License - Copyright 2025 io-pipeline

See [LICENSE](LICENSE) for full details.

## Support

For issues, questions, or contributions:
- **Issues**: [GitHub Issues](https://github.com/ai-pipestream/module-parser/issues)
- **Documentation**: See `/docs` directory
- **Platform**: [Pipestream AI Platform](https://github.com/ai-pipestream)

---

**Built with**: Apache Tika 3.2.1, Quarkus 3.x, Java 21, gRPC, Protocol Buffers