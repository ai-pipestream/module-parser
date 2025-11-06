# TODO: Tika Analysis Service

## Overview
Create a live Tika analysis service that can analyze documents from the repository service and provide detailed extraction statistics and insights.

## Features to Implement

### 1. Document Analysis Endpoint
Create a gRPC service endpoint that:
- Accepts a batch of document IDs or search criteria
- Fetches documents from repository service
- Runs them through Tika parser
- Returns detailed analysis results

### 2. Analysis Metrics
The service should provide:
- **Extraction Statistics**
  - Success/failure rates by file type
  - Average extraction time per document type
  - Content extraction rates (title, body, metadata)
  
- **Content Quality Metrics**
  - Average text length extracted by file type
  - Metadata field coverage statistics
  - Language detection accuracy
  
- **File Type Distribution**
  - MIME type distribution in corpus
  - Supported vs unsupported formats
  - Binary vs text file ratios

### 3. Detailed Tika Metadata Extraction
- Create `TikaResponse` protobuf message with all Tika-specific fields
- Map Tika metadata to structured format
- Preserve all metadata fields for analysis

### 4. Integration with Repository Service
- Query documents by:
  - Date range
  - File type
  - Drive/bucket
  - Tags
- Stream results for large datasets
- Cache analysis results for performance

### 5. Web UI Dashboard
- Real-time extraction statistics
- File type breakdown charts
- Extraction success/failure trends
- Drill-down into specific document issues

## Implementation Notes

```proto
// Example TikaResponse message
message TikaResponse {
  string doc_id = 1;
  TikaMetadata metadata = 2;
  TikaContent content = 3;
  TikaStatistics statistics = 4;
  repeated TikaWarning warnings = 5;
}

message TikaMetadata {
  map<string, string> core_metadata = 1;
  map<string, string> format_metadata = 2;
  map<string, string> custom_metadata = 3;
}

message TikaContent {
  string title = 1;
  string body = 2;
  string author = 3;
  string language = 4;
  repeated string keywords = 5;
  map<string, string> structured_content = 6;
}

message TikaStatistics {
  int64 processing_time_ms = 1;
  int32 content_length = 2;
  int32 metadata_field_count = 3;
  string detected_mime_type = 4;
  string detected_encoding = 5;
}
```

## Benefits
1. **Production Insights**: Understand what Tika can extract from real documents
2. **Quality Monitoring**: Track extraction quality over time
3. **Debugging Tool**: Identify problematic document types
4. **Planning Tool**: Understand what additional parsers might be needed
5. **Performance Monitoring**: Track parser performance metrics

## Future Enhancements
- Custom parser plugins for unsupported formats
- ML-based content enhancement
- Automatic re-parsing when Tika updates
- Comparison between different Tika versions
- Export analysis reports for stakeholders

## Related Components
- Repository Service (document source)
- Mapping Service (for pre/post transformations)
- OpenSearch (for storing analysis results)
- Monitoring/Metrics service

## Priority
Medium - Implement after repository service is stable and has documents to analyze