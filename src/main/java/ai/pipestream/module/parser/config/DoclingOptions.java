package ai.pipestream.module.parser.config;

import ai.docling.serve.api.convert.request.options.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Complete Docling parsing configuration options.
 *
 * This is a 1:1 mapping to ConvertDocumentOptions from docling-serve-api.
 * All 28 fields from the Docling API are exposed here.
 *
 * DO NOT add extra fields - keep it synchronized with ConvertDocumentOptions.
 */
@RegisterForReflection(targets = {
    InputFormat.class, OutputFormat.class, ImageRefMode.class, OcrEngine.class, 
    PdfBackend.class, TableFormerMode.class, ProcessingPipeline.class
})
@Schema(
    name = "DoclingOptions",
    description = "Complete Docling document parsing configuration - 1:1 mapping to ConvertDocumentOptions"
)
public record DoclingOptions(

    // ==================== Input/Output Formats ====================

    @JsonProperty("from_formats")
    @Schema(
        description = "Input format(s) to convert from.",
        examples = {"[\"pdf\", \"docx\"]", "[\"image\"]"}
    )
    List<InputFormat> fromFormats,

    @JsonProperty("to_formats")
    @Schema(
        description = "Output format(s) to convert to.",
        examples = {"[\"json\", \"md\"]", "[\"html\"]"}
    )
    List<OutputFormat> toFormats,

    @JsonProperty("image_export_mode")
    @Schema(
        description = "Image export mode. Allowed: placeholder, embedded, referenced",
        defaultValue = "embedded"
    )
    ImageRefMode imageExportMode,

    // ==================== OCR Configuration ====================

    @JsonProperty("do_ocr")
    @Schema(
        description = "Enable OCR for bitmap content",
        defaultValue = "true"
    )
    Boolean doOcr,

    @JsonProperty("force_ocr")
    @Schema(
        description = "Replace existing text with OCR-generated text",
        defaultValue = "false"
    )
    Boolean forceOcr,

    @JsonProperty("ocr_engine")
    @Schema(
        description = "OCR engine to use. Allowed: auto, easyocr, ocrmac, rapidocr, tesserocr, tesseract",
        defaultValue = "rapidocr"
    )
    OcrEngine ocrEngine,

    @JsonProperty("ocr_lang")
    @Schema(
        description = "Languages for OCR engine. Format depends on engine (Tesseract: eng,deu,fra | RapidOCR: ch,en | EasyOCR: en,de,fr)",
        examples = {"[\"eng\"]", "[\"eng\", \"deu\"]", "[\"ch\", \"en\"]"}
    )
    List<String> ocrLang,

    // ==================== PDF Processing ====================

    @JsonProperty("pdf_backend")
    @Schema(
        description = "PDF backend to use. Allowed: pypdfium2, dlparse_v1, dlparse_v2, dlparse_v4",
        defaultValue = "dlparse_v4"
    )
    PdfBackend pdfBackend,

    // ==================== Table Extraction ====================

    @JsonProperty("table_mode")
    @Schema(
        description = "Table structure mode. Allowed: fast, accurate",
        defaultValue = "accurate"
    )
    TableFormerMode tableMode,

    @JsonProperty("table_cell_matching")
    @Schema(
        description = "Match table cells predictions back to PDF cells. Can break output if PDF cells are merged"
    )
    Boolean tableCellMatching,

    @JsonProperty("do_table_structure")
    @Schema(
        description = "Extract table structure",
        defaultValue = "true"
    )
    Boolean doTableStructure,

    // ==================== Processing Control ====================

    @JsonProperty("pipeline")
    @Schema(
        description = "Processing pipeline for PDF or image files"
    )
    ProcessingPipeline pipeline,

    @JsonProperty("page_range")
    @Schema(
        description = "Only convert specific pages (1-indexed)",
        examples = {"[1, 2, 3]", "[1, 5, 10]"}
    )
    List<Integer> pageRange,

    @JsonProperty("document_timeout")
    @Schema(
        description = "Timeout for processing each document in seconds",
        defaultValue = "300"
    )
    Integer documentTimeout,

    @JsonProperty("abort_on_error")
    @Schema(
        description = "Abort processing on error",
        defaultValue = "false"
    )
    Boolean abortOnError,

    // ==================== Image Processing ====================

    @JsonProperty("include_images")
    @Schema(
        description = "Extract images from document",
        defaultValue = "true"
    )
    Boolean includeImages,

    @JsonProperty("images_scale")
    @Schema(
        description = "Scale factor for images",
        defaultValue = "2.0"
    )
    Double imagesScale,

    // ==================== Output Formatting ====================

    @JsonProperty("md_page_break_placeholder")
    @Schema(
        description = "Placeholder between pages in markdown output"
    )
    String mdPageBreakPlaceholder,

    // ==================== Content Enrichment ====================

    @JsonProperty("do_code_enrichment")
    @Schema(
        description = "Perform OCR code enrichment",
        defaultValue = "false"
    )
    Boolean doCodeEnrichment,

    @JsonProperty("do_formula_enrichment")
    @Schema(
        description = "Perform formula OCR, return LaTeX code",
        defaultValue = "false"
    )
    Boolean doFormulaEnrichment,

    // ==================== Picture AI Features ====================

    @JsonProperty("do_picture_classification")
    @Schema(
        description = "Classify pictures using AI",
        defaultValue = "false"
    )
    Boolean doPictureClassification,

    @JsonProperty("do_picture_description")
    @Schema(
        description = "Generate AI descriptions for pictures",
        defaultValue = "false"
    )
    Boolean doPictureDescription,

    @JsonProperty("picture_description_area_threshold")
    @Schema(
        description = "Minimum percentage of area for picture to be processed with AI models"
    )
    Double pictureDescriptionAreaThreshold,

    @JsonProperty("picture_description_local")
    @Schema(
        description = "Local vision-language model config for picture description (JSON object)"
    )
    String pictureDescriptionLocal,

    @JsonProperty("picture_description_api")
    @Schema(
        description = "API details for vision-language model in picture description (JSON object)"
    )
    String pictureDescriptionApi,

    // ==================== VLM Pipeline ====================

    @JsonProperty("vlm_pipeline_model")
    @Schema(
        description = "Preset local/API models for VLM pipeline"
    )
    String vlmPipelineModel,

    @JsonProperty("vlm_pipeline_model_local")
    @Schema(
        description = "Local vision-language model for VLM pipeline (Hugging Face model reference)"
    )
    String vlmPipelineModelLocal,

    @JsonProperty("vlm_pipeline_model_api")
    @Schema(
        description = "API details for vision-language model in VLM pipeline"
    )
    String vlmPipelineModelApi

) {

    /**
     * Creates default Docling options matching Docling API defaults.
     */
    public static DoclingOptions defaultOptions() {
        return new DoclingOptions(
            null,                   // from_formats (all formats)
            List.of(OutputFormat.JSON),        // to_formats (json for structured data)
            ImageRefMode.EMBEDDED,             // image_export_mode
            true,                   // do_ocr
            false,                  // force_ocr
            OcrEngine.RAPIDOCR,             // ocr_engine
            List.of(),              // ocr_lang (empty, engine chooses)
            PdfBackend.DLPARSE_V4,           // pdf_backend
            TableFormerMode.ACCURATE,             // table_mode
            null,                   // table_cell_matching
            true,                   // do_table_structure
            null,                   // pipeline
            null,                   // page_range (all pages)
            300,                    // document_timeout
            false,                  // abort_on_error
            true,                   // include_images
            2.0,                    // images_scale
            null,                   // md_page_break_placeholder
            false,                  // do_code_enrichment
            false,                  // do_formula_enrichment
            false,                  // do_picture_classification
            false,                  // do_picture_description
            null,                   // picture_description_area_threshold
            null,                   // picture_description_local
            null,                   // picture_description_api
            null,                   // vlm_pipeline_model
            null,                   // vlm_pipeline_model_local
            null                    // vlm_pipeline_model_api
        );
    }

    /**
     * Options for comprehensive extraction with all formats.
     */
    public static DoclingOptions allFormats() {
        return new DoclingOptions(
            null,
            List.of(OutputFormat.JSON, OutputFormat.MARKDOWN, OutputFormat.HTML, OutputFormat.TEXT),
            ImageRefMode.EMBEDDED,
            true,
            false,
            OcrEngine.RAPIDOCR,
            List.of(),
            PdfBackend.DLPARSE_V4,
            TableFormerMode.ACCURATE,
            null,
            true,
            null,
            null,
            300,
            false,
            true,
            2.0,
            null,
            false,
            false,
            true,                   // Enable picture classification
            true,                   // Enable picture description
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    /**
     * Options optimized for fast processing.
     */
    public static DoclingOptions fastProcessing() {
        return new DoclingOptions(
            null,
            List.of(OutputFormat.JSON, OutputFormat.MARKDOWN),
            ImageRefMode.PLACEHOLDER,          // Faster image mode
            false,                  // Disable OCR
            false,
            OcrEngine.RAPIDOCR,
            List.of(),
            PdfBackend.DLPARSE_V4,
            TableFormerMode.FAST,                 // Fast table mode
            null,
            true,
            null,
            null,
            60,                     // Shorter timeout
            false,
            false,                  // Skip images
            1.0,                    // Lower scale
            null,
            false,
            false,
            false,                  // No AI features
            false,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
