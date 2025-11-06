# Tika Actual Metadata Fields

## What Apache Tika ACTUALLY Extracts

Based on Apache Tika's actual capabilities, here's what it really provides:

### ✅ DEFINITELY Available from Tika

#### Core Document Metadata (Dublin Core)
- `dc:title` - Document title
- `dc:creator` - Creator/author
- `dc:subject` - Subject
- `dc:description` - Description
- `dc:publisher` - Publisher
- `dc:contributor` - Contributors
- `dc:date` - Date
- `dc:type` - Document type
- `dc:format` - Format
- `dc:identifier` - Unique identifier
- `dc:source` - Source
- `dc:language` - Language
- `dc:relation` - Related resources
- `dc:coverage` - Coverage
- `dc:rights` - Rights/copyright

#### File/Content Metadata
- `Content-Type` - MIME type detected
- `Content-Length` - File size
- `Content-Encoding` - Character encoding
- `Content-Language` - Language detected
- `resourceName` - Filename
- `X-Parsed-By` - Which parser was used

#### Common Office Document Fields
- `meta:author` - Author
- `meta:last-author` - Last person to save
- `meta:creation-date` - Creation date
- `meta:save-date` - Last save date
- `meta:page-count` - Number of pages
- `meta:word-count` - Word count
- `meta:character-count` - Character count
- `meta:paragraph-count` - Paragraph count
- `meta:line-count` - Line count
- `Application-Name` - Creating application
- `Application-Version` - App version
- `Company` - Company
- `Manager` - Manager field
- `Template` - Document template
- `Revision-Number` - Revision number
- `Edit-Time` - Total editing time

#### PDF Specific (when parsing PDFs)
- `pdf:PDFVersion` - PDF version
- `pdf:producer` - PDF producer
- `pdf:encrypted` - Is encrypted
- `xmpTPg:NPages` - Page count
- `pdf:hasXFA` - Has forms
- `pdf:hasXMP` - Has XMP metadata
- `pdf:fontName` - Fonts used (multiple values)
- `access_permission:*` - Various permissions

#### Image/EXIF Metadata (when parsing images)
- `tiff:ImageWidth` - Image width
- `tiff:ImageLength` - Image height
- `tiff:BitsPerSample` - Bits per sample
- `tiff:Make` - Camera make
- `tiff:Model` - Camera model
- `exif:DateTimeOriginal` - Date taken
- `exif:FNumber` - F-stop
- `exif:ExposureTime` - Exposure time
- `exif:ISOSpeedRatings` - ISO speed
- `exif:FocalLength` - Focal length
- `exif:Flash` - Flash used
- `GPS:*` - GPS coordinates (if available)

#### Email Metadata (when parsing emails)
- `Message-From` - From address
- `Message-To` - To addresses
- `Message-Cc` - CC addresses
- `Message-Bcc` - BCC addresses
- `subject` - Email subject
- `dc:date` - Sent date
- Various email headers

#### Embedded Resources
- Tika DOES extract embedded documents
- Each embedded doc gets its own metadata
- Tracks embedding relationships

### ⚠️ PARTIALLY Available (depends on document)

#### Language Detection
- Tika CAN detect language but needs LanguageDetector configured
- Returns ISO language codes (en, es, fr, etc.)
- No confidence scores by default

#### Geographic Extraction
- Requires GeoTopicParser to be enabled
- Can extract place names and coordinates from text
- Not enabled by default

#### Structured Content
- Tables: Some parsers extract tables (especially from HTML/XML)
- Forms: PDF parser can detect form fields
- Links: HTML parser extracts links
- Headings: Some parsers preserve structure

### ❌ NOT Directly from Tika (I was optimistic)

These would need additional processing:
- Table of contents with page numbers
- Section-by-section content breakdown
- Annotation/comment details with authors
- Language confidence scores
- Detailed form field types/options
- Video/audio codec details (basic media info only)
- Lens model for photos
- Email attachment details beyond count
- JavaScript detection in PDFs
- Document signatures verification
- Encryption details beyond yes/no

## Real Example of Tika Metadata

Here's actual metadata from a Word document:

```properties
Content-Type=application/vnd.openxmlformats-officedocument.wordprocessingml.document
dc:creator=John Smith
meta:last-author=Jane Doe
meta:creation-date=2024-01-15T10:30:00Z
meta:save-date=2024-03-20T14:45:00Z
meta:page-count=15
meta:word-count=3500
meta:character-count=21000
meta:paragraph-count=125
meta:line-count=450
Application-Name=Microsoft Office Word
Application-Version=16.0.12345.20000
Company=Acme Corp
Template=Normal.dotm
Revision-Number=7
Edit-Time=12000000
dc:title=Project Proposal
dc:subject=Q1 Planning
dc:description=Proposal for new features
keywords=proposal, features, planning
Content-Length=458752
```

## What This Means for TikaResponse

The TikaResponse proto I created is **aspirational** - it has fields for everything Tika COULD provide across all document types. In practice:

1. **Most fields will be empty** for any given document
2. **Different document types populate different fields**
3. **The raw_metadata map will always have everything** Tika found
4. **Special parsers (OCR, Geo, Scientific) need to be enabled**

## Recommendation

We should:
1. Keep the comprehensive TikaResponse structure (it's good to have placeholders)
2. Focus on populating what Tika actually provides
3. Use the raw_metadata map as the source of truth
4. Let the mapping service decide what's important

The proto is intentionally over-complete so we don't lose any data Tika might extract!