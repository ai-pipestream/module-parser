Parser Module

Overview
The Parser is a Pipeline module that extracts text and rich metadata from many document formats using Apache Tika. It runs as a Quarkus service and exposes gRPC (primary) plus developer REST helpers. In live development it includes a Quinoa-powered UI for configuration and testing.

At a glance
- Purpose: Turn a PipeDoc’s blob into searchable text and structured metadata
- Interfaces: gRPC PipeStepProcessor + REST helper endpoints
- Core engine: Apache Tika (AutoDetectParser) with custom options
- Outputs: PipeDoc with populated SearchMetadata and optional structured_data (TikaResponse)
- Build: Gradle + Quarkus; Quinoa UI (src/main/ui-vue)
- Deploy: Dockerfiles provided (JVM, native)
- Integrations: Works with the web proxy during live dev for gRPC requests from the UI

Features
- Parser types and strategies (toggleable in config)
  - Auto-detect content type (Tika AutoDetectParser)
  - Embedded document extraction (attachments, embedded objects)
  - Title extraction with filename fallback
  - MIME filtering via allow-list (supportedMimeTypes)
  - Greedy backoff strategy to keep parsing resilient on bad inputs
  - Special parsers:
    - GeoTopic parser (location extraction)
    - OCR parser (images/scanned PDFs; requires system OCR tooling)
    - Scientific parser (academia/technical docs)
  - Office EMF parser toggle to avoid problematic EMF files
  - Outline extraction (neutral DocOutline) from:
    - EPUB (table of contents)
    - HTML (h1..h6, with include/exclude CSS options)
    - Markdown (headings/links)
- Rich metadata
  - Tika metadata map (normalized, length-limited)
  - Auto title, content type, and encoding detection
  - Optional comprehensive TikaResponse packed into PipeDoc.structured_data

Supported via gRPC (content types)
The service accepts a ModuleProcessRequest with a PipeDoc containing a Blob. It supports, through Apache Tika and module options, these specific categories:
- PDF: application/pdf (including scanned PDFs when OCR is enabled)
- Microsoft Office:
  - Word: application/msword, application/vnd.openxmlformats-officedocument.wordprocessingml.document (.doc, .docx)
  - PowerPoint: application/vnd.ms-powerpoint, application/vnd.openxmlformats-officedocument.presentationml.presentation (.ppt, .pptx)
  - Excel: application/vnd.ms-excel, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet (.xls, .xlsx)
- OpenDocument: application/vnd.oasis.opendocument.* (text, spreadsheet, presentation)
- EPUB: application/epub+zip (with table of contents to DocOutline)
- HTML/XML: text/html, text/xml, application/xml (outline/heading extraction optional)
- JSON and text: application/json, text/plain, text/markdown
- Images (OCR optional): image/png, image/jpeg, image/tiff, image/gif, image/webp
- Archives/containers: application/zip, application/x-tar, application/gzip (processed when embedded extraction is enabled)
- Fonts (special case): font/ttf, font/otf, font/woff, font/woff2, pfa/pfb — bypassed for content parsing; title derived from filename

How it works
1. Receives ModuleProcessRequest over gRPC
2. Detects type and parses with Tika (respecting ParserConfig options)
3. Builds/updates SearchMetadata (title, body, contentType, optional DocOutline)
4. Optionally packs a full io.pipeline.parsed.data.tika.v1.TikaResponse into PipeDoc.structured_data
5. Returns ModuleProcessResponse with success logs and the updated PipeDoc

Configuration
- Provided as ProcessConfiguration.custom_json_config mapped to ParserConfig, which includes:
  - parsingOptions
    - maxContentLength, extractMetadata, maxMetadataValueLength, parseTimeoutSeconds
  - advancedOptions
    - enableGeoTopicParser, disableEmfParser, extractEmbeddedDocs, maxRecursionDepth,
      enableOcrParser, enableScientificParser, enableGreedyBackoff
  - contentTypeHandling
    - enableTitleExtraction, fallbackToFilename, supportedMimeTypes[]
  - outlineExtraction
    - enableEpubOutline, enableHtmlOutline, htmlIncludeCss, htmlExcludeCss,
      htmlStripScripts, enableMarkdownOutline, htmlMinHeadingLevel, htmlMaxHeadingLevel,
      htmlGenerateIds
  - errorHandling
    - ignoreTikaException, fallbackToPlainText, logParsingErrors
  - config_id (auto-generated when omitted)

Service endpoints
- gRPC: Implements PipeStepProcessor
  - processData(ModuleProcessRequest) → ModuleProcessResponse
  - testProcessData(ModuleProcessRequest) → ModuleProcessResponse (useful for UI/tests)
  - getServiceRegistration(RegistrationRequest) → ServiceRegistrationMetadata with embedded OpenAPI schema (enhanced at runtime)
- REST helpers: small developer endpoints exist to support the UI

Quinoa UI and web proxy (live dev)
- Quarkus Quinoa serves a Vue UI under src/main/ui-vue
- In live dev, the UI talks through the platform’s Web Proxy for gRPC calls
- See docs/architecture/frontend/Web_Proxy.md
- For module UI rendering and schema-driven forms, see:
  - docs/architecture/Module_rendering_architecture.md
  - docs/architecture/Module_rendering_guide.md
  - docs/architecture/Pipeline_design.md

Build and run (Gradle + Quarkus)
- Dev mode (with live UI via Quinoa and the web proxy):
  - ./gradlew :modules:parser:quarkusDev
- JVM build:
  - ./gradlew :modules:parser:build
- Native build (optional):
  - Standard Quarkus native build flags

Docker
- JVM image:
  - cd modules/parser
  - docker build -f src/main/docker/Dockerfile.jvm -t pipeline/parser:latest .
  - docker run --rm -p 8080:8080 pipeline/parser:latest
- Native images:
  - See src/main/docker for native variants

Notes & tips
- Use supportedMimeTypes to restrict accepted formats for a pipeline
- Enable OCR only when you need it (costly) and ensure the environment provides OCR tooling
- For Office archives with EMF glitches, set disableEmfParser=true
- For EPUB/HTML/Markdown documents, enable outlineExtraction to populate SearchMetadata.doc_outline

References
- GRPC build and communication patterns: docs/architecture/GRPC_Build.md, docs/architecture/GRPC_Communication_Patterns.md
- Module rendering architecture: docs/architecture/Module_rendering_architecture.md
- Module rendering guide: docs/architecture/Module_rendering_guide.md
- Pipeline design: docs/architecture/Pipeline_design.md
- Web Proxy (frontend): docs/architecture/frontend/Web_Proxy.md