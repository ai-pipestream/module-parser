package ai.pipestream.module.parser.docling;

import ai.docling.core.DoclingDocument;
import ai.pipestream.parsed.data.docling.v1.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DoclingDocumentMapper - field-by-field mapping from Java to Proto.
 * NO JSON - pure strongly-typed mapping.
 */
class DoclingDocumentMapperTest {

    @Test
    void testMapDocumentOrigin() {
        // Given: Java DocumentOrigin
        DoclingDocument.DocumentOrigin javaOrigin = DoclingDocument.DocumentOrigin.builder()
            .mimetype("application/pdf")
            .filename("test.pdf")
            .uri("file:///test.pdf")
            .build();

        // When: Map to proto
        DoclingDocument javaDoc = DoclingDocument.builder()
            .name("test-doc")
            .origin(javaOrigin)
            .build();

        ai.pipestream.parsed.data.docling.v1.DoclingDocument protoDoc =
            DoclingDocumentMapper.map(javaDoc);

        // Then: Verify proto fields match
        assertNotNull(protoDoc);
        assertEquals("test-doc", protoDoc.getName());
        assertTrue(protoDoc.hasOrigin());
        assertEquals("application/pdf", protoDoc.getOrigin().getMimetype());
        assertEquals("test.pdf", protoDoc.getOrigin().getFilename());
        assertEquals("file:///test.pdf", protoDoc.getOrigin().getUri());
    }

    @Test
    void testMapTableData() {
        // Given: Java TableCell
        DoclingDocument.BoundingBox bbox = DoclingDocument.BoundingBox.builder()
            .l(10.0)
            .t(20.0)
            .r(100.0)
            .b(50.0)
            .build();

        DoclingDocument.TableCell cell = DoclingDocument.TableCell.builder()
            .bbox(bbox)
            .text("Cell Value")
            .rowSpan(1)
            .colSpan(1)
            .columnHeader(true)
            .build();

        // Java TableData with grid
        DoclingDocument.TableData javaTableData = DoclingDocument.TableData.builder()
            .numRows(2)
            .numCols(3)
            .grid(List.of(List.of(cell)))
            .build();

        // When: Map via TableItem
        DoclingDocument.TableItem javaTableItem = DoclingDocument.TableItem.builder()
            .selfRef("#/tables/0")
            .data(javaTableData)
            .build();

        DoclingDocument javaDoc = DoclingDocument.builder()
            .name("table-doc")
            .table(javaTableItem)
            .build();

        ai.pipestream.parsed.data.docling.v1.DoclingDocument protoDoc =
            DoclingDocumentMapper.map(javaDoc);

        // Then: Verify table structure
        assertNotNull(protoDoc);
        assertEquals(1, protoDoc.getTablesCount());

        TableItem protoTable = protoDoc.getTables(0);
        assertEquals("#/tables/0", protoTable.getSelfRef());
        assertTrue(protoTable.hasData());

        TableData protoTableData = protoTable.getData();
        assertEquals(2, protoTableData.getNumRows());
        assertEquals(3, protoTableData.getNumCols());
        assertEquals(1, protoTableData.getGridCount());

        // Verify grid row
        TableRow protoRow = protoTableData.getGrid(0);
        assertEquals(1, protoRow.getCellsCount());

        // Verify cell
        TableCell protoCell = protoRow.getCells(0);
        assertEquals("Cell Value", protoCell.getText());
        assertEquals(1, protoCell.getRowSpan());
        assertEquals(1, protoCell.getColSpan());
        assertTrue(protoCell.getColumnHeader());

        // Verify bounding box
        assertTrue(protoCell.hasBbox());
        assertEquals(10.0, protoCell.getBbox().getL(), 0.001);
        assertEquals(20.0, protoCell.getBbox().getT(), 0.001);
        assertEquals(100.0, protoCell.getBbox().getR(), 0.001);
        assertEquals(50.0, protoCell.getBbox().getB(), 0.001);
    }

    @Test
    void testMapRefItem() {
        // Given: Java RefItem
        DoclingDocument.RefItem javaRef = DoclingDocument.RefItem.builder()
            .ref("#/texts/5")
            .build();

        DoclingDocument.GroupItem javaGroup = DoclingDocument.GroupItem.builder()
            .selfRef("#/groups/0")
            .name("test-group")
            .label(DoclingDocument.GroupLabel.SECTION)
            .child(javaRef)
            .build();

        DoclingDocument javaDoc = DoclingDocument.builder()
            .name("ref-doc")
            .group(javaGroup)
            .build();

        // When: Map to proto
        ai.pipestream.parsed.data.docling.v1.DoclingDocument protoDoc =
            DoclingDocumentMapper.map(javaDoc);

        // Then: Verify RefItem mapping
        assertNotNull(protoDoc);
        assertEquals(1, protoDoc.getGroupsCount());

        GroupItem protoGroup = protoDoc.getGroups(0);
        assertEquals("#/groups/0", protoGroup.getSelfRef());
        assertEquals("test-group", protoGroup.getName());
        assertEquals(GroupLabel.GROUP_LABEL_SECTION, protoGroup.getLabel());
        assertEquals(1, protoGroup.getChildrenCount());

        RefItem protoRef = protoGroup.getChildren(0);
        assertEquals("#/texts/5", protoRef.getRef());
    }

    @Test
    void testMapTextItemWithLabel() {
        // Given: Java TextItem (one of the BaseTextItem implementations)
        DoclingDocument.TextItem javaTextItem = DoclingDocument.TextItem.builder()
            .selfRef("#/texts/0")
            .label(DoclingDocument.DocItemLabel.PARAGRAPH)
            .text("This is a paragraph.")
            .orig("This is a paragraph.")
            .contentLayer(DoclingDocument.ContentLayer.BODY)
            .build();

        DoclingDocument javaDoc = DoclingDocument.builder()
            .name("text-doc")
            .text(javaTextItem)
            .build();

        // When: Map to proto
        ai.pipestream.parsed.data.docling.v1.DoclingDocument protoDoc =
            DoclingDocumentMapper.map(javaDoc);

        // Then: Verify BaseTextItem oneof is populated correctly
        assertNotNull(protoDoc);
        assertEquals(1, protoDoc.getTextsCount());

        BaseTextItem protoTextItem = protoDoc.getTexts(0);
        assertTrue(protoTextItem.hasText()); // oneof case

        TextItem protoText = protoTextItem.getText();
        assertTrue(protoText.hasBase());

        TextItemBase base = protoText.getBase();
        assertEquals("#/texts/0", base.getSelfRef());
        assertEquals("This is a paragraph.", base.getText());
        assertEquals("This is a paragraph.", base.getOrig());
        assertEquals(ContentLayer.CONTENT_LAYER_BODY, base.getContentLayer());
        assertEquals(DocItemLabel.DOC_ITEM_LABEL_PARAGRAPH, base.getLabel());
    }

    @Test
    void testMapNull() {
        // Given: null Java document
        // When: Map to proto
        ai.pipestream.parsed.data.docling.v1.DoclingDocument protoDoc =
            DoclingDocumentMapper.map(null);

        // Then: Return default instance, not null
        assertNotNull(protoDoc);
        assertEquals(ai.pipestream.parsed.data.docling.v1.DoclingDocument.getDefaultInstance(), protoDoc);
    }

    @Test
    void testMapProvenanceAndFormatting() {
        // Given: TextItem with provenance and formatting
        DoclingDocument.BoundingBox bbox = DoclingDocument.BoundingBox.builder()
            .l(10.0)
            .t(20.0)
            .r(100.0)
            .b(50.0)
            .build();

        DoclingDocument.ProvenanceItem provenance = DoclingDocument.ProvenanceItem.builder()
            .pageNo(1)
            .bbox(bbox)
            .charspan(List.of(0, 10))
            .build();

        DoclingDocument.Formatting formatting = DoclingDocument.Formatting.builder()
            .bold(true)
            .italic(false)
            .underline(true)
            .strikethrough(false)
            .script(DoclingDocument.Script.BASELINE)
            .build();

        DoclingDocument.TextItem javaTextItem = DoclingDocument.TextItem.builder()
            .selfRef("#/texts/0")
            .label(DoclingDocument.DocItemLabel.PARAGRAPH)
            .text("Test text with formatting")
            .orig("Test text with formatting")
            .contentLayer(DoclingDocument.ContentLayer.BODY)
            .prov(provenance)
            .formatting(formatting)
            .build();

        DoclingDocument javaDoc = DoclingDocument.builder()
            .name("formatting-doc")
            .text(javaTextItem)
            .build();

        // When: Map to proto
        ai.pipestream.parsed.data.docling.v1.DoclingDocument protoDoc =
            DoclingDocumentMapper.map(javaDoc);

        // Then: Verify provenance and formatting
        assertNotNull(protoDoc);
        assertEquals(1, protoDoc.getTextsCount());

        BaseTextItem protoTextItem = protoDoc.getTexts(0);
        assertTrue(protoTextItem.hasText());

        TextItem protoText = protoTextItem.getText();
        TextItemBase base = protoText.getBase();

        // Verify provenance
        assertEquals(1, base.getProvCount());
        ProvenanceItem protoProv = base.getProv(0);
        assertEquals(1, protoProv.getPageNo());
        assertTrue(protoProv.hasBbox());
        assertEquals(10.0, protoProv.getBbox().getL(), 0.001);
        assertEquals(2, protoProv.getCharspanCount());
        assertEquals(0, protoProv.getCharspan(0));
        assertEquals(10, protoProv.getCharspan(1));

        // Verify formatting
        assertTrue(base.hasFormatting());
        Formatting protoFormatting = base.getFormatting();
        assertTrue(protoFormatting.getBold());
        assertFalse(protoFormatting.getItalic());
        assertTrue(protoFormatting.getUnderline());
        assertFalse(protoFormatting.getStrikethrough());
        assertEquals(Script.SCRIPT_BASELINE, protoFormatting.getScript());
    }

    @Test
    void testMapImageRef() {
        // Given: PageItem with ImageRef
        DoclingDocument.Size size = DoclingDocument.Size.builder()
            .width(800.0)
            .height(600.0)
            .build();

        DoclingDocument.ImageRef imageRef = DoclingDocument.ImageRef.builder()
            .mimetype("image/png")
            .dpi(300)
            .size(size)
            .uri("file:///path/to/image.png")
            .build();

        DoclingDocument.PageItem pageItem = DoclingDocument.PageItem.builder()
            .pageNo(1)
            .size(size)
            .image(imageRef)
            .build();

        DoclingDocument javaDoc = DoclingDocument.builder()
            .name("page-doc")
            .page("1", pageItem)
            .build();

        // When: Map to proto
        ai.pipestream.parsed.data.docling.v1.DoclingDocument protoDoc =
            DoclingDocumentMapper.map(javaDoc);

        // Then: Verify ImageRef
        assertNotNull(protoDoc);
        assertEquals(1, protoDoc.getPagesCount());

        PageItem protoPage = protoDoc.getPagesMap().get("1");
        assertNotNull(protoPage);
        assertEquals(1, protoPage.getPageNo());

        assertTrue(protoPage.hasImage());
        ImageRef protoImageRef = protoPage.getImage();
        assertEquals("image/png", protoImageRef.getMimetype());
        assertEquals(300, protoImageRef.getDpi());
        assertEquals("file:///path/to/image.png", protoImageRef.getUri());

        assertTrue(protoImageRef.hasSize());
        assertEquals(800.0, protoImageRef.getSize().getWidth(), 0.001);
        assertEquals(600.0, protoImageRef.getSize().getHeight(), 0.001);
    }

    @Test
    void testMapPictureItemWithMeta() {
        // Given: PictureItem with metadata
        DoclingDocument.SummaryMetaField summary = DoclingDocument.SummaryMetaField.builder()
            .text("A diagram showing the system architecture")
            .confidence(0.95)
            .createdBy("ai-model-v1")
            .build();

        DoclingDocument.PictureClassificationPrediction pred =
            DoclingDocument.PictureClassificationPrediction.builder()
            .className("diagram")
            .confidence(0.92)
            .createdBy("classifier-v2")
            .build();

        DoclingDocument.PictureClassificationMetaField classification =
            DoclingDocument.PictureClassificationMetaField.builder()
            .prediction(pred)
            .build();

        DoclingDocument.PictureMeta meta = DoclingDocument.PictureMeta.builder()
            .summary(summary)
            .classification(classification)
            .build();

        DoclingDocument.PictureItem pictureItem = DoclingDocument.PictureItem.builder()
            .selfRef("#/pictures/0")
            .label("picture")
            .contentLayer(DoclingDocument.ContentLayer.BODY)
            .meta(meta)
            .build();

        DoclingDocument javaDoc = DoclingDocument.builder()
            .name("picture-doc")
            .picture(pictureItem)
            .build();

        // When: Map to proto
        ai.pipestream.parsed.data.docling.v1.DoclingDocument protoDoc =
            DoclingDocumentMapper.map(javaDoc);

        // Then: Verify PictureItem and metadata
        assertNotNull(protoDoc);
        assertEquals(1, protoDoc.getPicturesCount());

        PictureItem protoPicture = protoDoc.getPictures(0);
        assertEquals("#/pictures/0", protoPicture.getSelfRef());
        assertEquals("picture", protoPicture.getLabel());
        assertEquals(ContentLayer.CONTENT_LAYER_BODY, protoPicture.getContentLayer());

        assertTrue(protoPicture.hasMeta());
        PictureMeta protoMeta = protoPicture.getMeta();

        // Verify summary
        assertTrue(protoMeta.hasSummary());
        assertEquals("A diagram showing the system architecture", protoMeta.getSummary().getText());
        assertEquals(0.95, protoMeta.getSummary().getConfidence(), 0.001);
        assertEquals("ai-model-v1", protoMeta.getSummary().getCreatedBy());

        // Verify classification
        assertTrue(protoMeta.hasClassification());
        assertEquals(1, protoMeta.getClassification().getPredictionsCount());
        PictureClassificationPrediction protoPred = protoMeta.getClassification().getPredictions(0);
        assertEquals("diagram", protoPred.getClassName());
        assertEquals(0.92, protoPred.getConfidence(), 0.001);
        assertEquals("classifier-v2", protoPred.getCreatedBy());
    }

    @Test
    void testMapKeyValueItemWithGraph() {
        // Given: KeyValueItem with graph structure
        DoclingDocument.ProvenanceItem prov = DoclingDocument.ProvenanceItem.builder()
            .pageNo(1)
            .bbox(DoclingDocument.BoundingBox.builder()
                .l(10.0).t(20.0).r(100.0).b(50.0)
                .build())
            .build();

        DoclingDocument.GraphCell keyCell = DoclingDocument.GraphCell.builder()
            .label(DoclingDocument.GraphCellLabel.KEY)
            .cellId(1)
            .text("Name:")
            .orig("Name:")
            .prov(prov)
            .build();

        DoclingDocument.GraphCell valueCell = DoclingDocument.GraphCell.builder()
            .label(DoclingDocument.GraphCellLabel.VALUE)
            .cellId(2)
            .text("John Doe")
            .orig("John Doe")
            .build();

        DoclingDocument.GraphLink link = DoclingDocument.GraphLink.builder()
            .label(DoclingDocument.GraphLinkLabel.TO_VALUE)
            .sourceCellId(1)
            .targetCellId(2)
            .build();

        DoclingDocument.GraphData graph = DoclingDocument.GraphData.builder()
            .cell(keyCell)
            .cell(valueCell)
            .link(link)
            .build();

        DoclingDocument.KeyValueItem kvItem = DoclingDocument.KeyValueItem.builder()
            .selfRef("#/key_value_items/0")
            .label("key_value_region")
            .contentLayer(DoclingDocument.ContentLayer.BODY)
            .graph(graph)
            .build();

        DoclingDocument javaDoc = DoclingDocument.builder()
            .name("kv-doc")
            .keyValueItem(kvItem)
            .build();

        // When: Map to proto
        ai.pipestream.parsed.data.docling.v1.DoclingDocument protoDoc =
            DoclingDocumentMapper.map(javaDoc);

        // Then: Verify KeyValueItem and graph structure
        assertNotNull(protoDoc);
        assertEquals(1, protoDoc.getKeyValueItemsCount());

        KeyValueItem protoKv = protoDoc.getKeyValueItems(0);
        assertEquals("#/key_value_items/0", protoKv.getSelfRef());
        assertEquals("key_value_region", protoKv.getLabel());

        assertTrue(protoKv.hasGraph());
        GraphData protoGraph = protoKv.getGraph();

        // Verify cells
        assertEquals(2, protoGraph.getCellsCount());
        GraphCell protoKeyCell = protoGraph.getCells(0);
        assertEquals(GraphCellLabel.GRAPH_CELL_LABEL_KEY, protoKeyCell.getLabel());
        assertEquals(1, protoKeyCell.getCellId());
        assertEquals("Name:", protoKeyCell.getText());
        assertTrue(protoKeyCell.hasProv());

        GraphCell protoValueCell = protoGraph.getCells(1);
        assertEquals(GraphCellLabel.GRAPH_CELL_LABEL_VALUE, protoValueCell.getLabel());
        assertEquals(2, protoValueCell.getCellId());
        assertEquals("John Doe", protoValueCell.getText());

        // Verify links
        assertEquals(1, protoGraph.getLinksCount());
        GraphLink protoLink = protoGraph.getLinks(0);
        assertEquals(GraphLinkLabel.GRAPH_LINK_LABEL_TO_VALUE, protoLink.getLabel());
        assertEquals(1, protoLink.getSourceCellId());
        assertEquals(2, protoLink.getTargetCellId());
    }
}
