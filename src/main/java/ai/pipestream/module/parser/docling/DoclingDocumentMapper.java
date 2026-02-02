package ai.pipestream.module.parser.docling;

import ai.docling.core.DoclingDocument;
import ai.pipestream.parsed.data.docling.v1.*;
import org.jboss.logging.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Maps ai.docling.core.DoclingDocument (Java) to ai.pipestream.parsed.data.docling.v1.DoclingDocument (Proto).
 *
 * This is a proper field-by-field mapper with NO JSON.
 * Each field is explicitly mapped to maintain strong typing throughout the gRPC stack.
 */
public class DoclingDocumentMapper {

    private static final Logger LOG = Logger.getLogger(DoclingDocumentMapper.class);

    /**
     * Maps Java DoclingDocument to Proto DoclingDocument.
     *
     * @param javaDoc The Java DoclingDocument from docling-java library
     * @return Proto DoclingDocument message
     */
    public static ai.pipestream.parsed.data.docling.v1.DoclingDocument map(DoclingDocument javaDoc) {
        if (javaDoc == null) {
            return ai.pipestream.parsed.data.docling.v1.DoclingDocument.getDefaultInstance();
        }

        LOG.debugf("Mapping DoclingDocument field-by-field: schema=%s, version=%s, name=%s",
                  javaDoc.getSchemaName(), javaDoc.getVersion(), javaDoc.getName());

        ai.pipestream.parsed.data.docling.v1.DoclingDocument.Builder builder =
            ai.pipestream.parsed.data.docling.v1.DoclingDocument.newBuilder();

        // Map simple string fields
        if (javaDoc.getSchemaName() != null) {
            builder.setSchemaName(javaDoc.getSchemaName());
        }
        if (javaDoc.getVersion() != null) {
            builder.setVersion(javaDoc.getVersion());
        }
        if (javaDoc.getName() != null) {
            builder.setName(javaDoc.getName());
        }

        // Map origin
        if (javaDoc.getOrigin() != null) {
            builder.setOrigin(mapDocumentOrigin(javaDoc.getOrigin()));
        }

        // Map body (GroupItem)
        if (javaDoc.getBody() != null) {
            builder.setBody(mapGroupItem(javaDoc.getBody()));
        }

        // Map groups
        if (javaDoc.getGroups() != null) {
            javaDoc.getGroups().forEach(group -> builder.addGroups(mapGroupItem(group)));
        }

        // Map texts
        if (javaDoc.getTexts() != null) {
            javaDoc.getTexts().forEach(text -> builder.addTexts(mapBaseTextItem(text)));
        }

        // Map pictures
        if (javaDoc.getPictures() != null) {
            javaDoc.getPictures().forEach(picture -> builder.addPictures(mapPictureItem(picture)));
        }

        // Map tables
        if (javaDoc.getTables() != null) {
            javaDoc.getTables().forEach(table -> builder.addTables(mapTableItem(table)));
        }

        // Map key_value_items
        if (javaDoc.getKeyValueItems() != null) {
            javaDoc.getKeyValueItems().forEach(kv -> builder.addKeyValueItems(mapKeyValueItem(kv)));
        }

        // Map form_items
        if (javaDoc.getFormItems() != null) {
            javaDoc.getFormItems().forEach(form -> builder.addFormItems(mapFormItem(form)));
        }

        // Map pages
        if (javaDoc.getPages() != null) {
            javaDoc.getPages().forEach((key, page) ->
                builder.putPages(key, mapPageItem(page))
            );
        }

        return builder.build();
    }

    private static DocumentOrigin mapDocumentOrigin(DoclingDocument.DocumentOrigin javaOrigin) {
        DocumentOrigin.Builder builder = DocumentOrigin.newBuilder();

        if (javaOrigin.getMimetype() != null) {
            builder.setMimetype(javaOrigin.getMimetype());
        }
        if (javaOrigin.getBinaryHash() != null) {
            builder.setBinaryHash(javaOrigin.getBinaryHash().toString());
        }
        if (javaOrigin.getFilename() != null) {
            builder.setFilename(javaOrigin.getFilename());
        }
        if (javaOrigin.getUri() != null) {
            builder.setUri(javaOrigin.getUri());
        }

        return builder.build();
    }

    private static GroupItem mapGroupItem(DoclingDocument.GroupItem javaGroup) {
        GroupItem.Builder builder = GroupItem.newBuilder();

        if (javaGroup.getSelfRef() != null) {
            builder.setSelfRef(javaGroup.getSelfRef());
        }
        if (javaGroup.getParent() != null) {
            builder.setParent(mapRefItem(javaGroup.getParent()));
        }
        if (javaGroup.getChildren() != null) {
            javaGroup.getChildren().forEach(child -> builder.addChildren(mapRefItem(child)));
        }
        if (javaGroup.getName() != null) {
            builder.setName(javaGroup.getName());
        }
        if (javaGroup.getLabel() != null) {
            builder.setLabel(mapGroupLabel(javaGroup.getLabel()));
        }

        return builder.build();
    }

    private static GroupLabel mapGroupLabel(DoclingDocument.GroupLabel javaLabel) {
        return switch (javaLabel) {
            case LIST -> GroupLabel.GROUP_LABEL_LIST;
            case ORDERED_LIST -> GroupLabel.GROUP_LABEL_ORDERED_LIST;
            case CHAPTER -> GroupLabel.GROUP_LABEL_CHAPTER;
            case SECTION -> GroupLabel.GROUP_LABEL_SECTION;
            case SHEET -> GroupLabel.GROUP_LABEL_SHEET;
            case SLIDE -> GroupLabel.GROUP_LABEL_SLIDE;
            case FORM_AREA -> GroupLabel.GROUP_LABEL_FORM_AREA;
            case KEY_VALUE_AREA -> GroupLabel.GROUP_LABEL_KEY_VALUE_AREA;
            case COMMENT_SECTION -> GroupLabel.GROUP_LABEL_COMMENT_SECTION;
            case INLINE -> GroupLabel.GROUP_LABEL_INLINE;
            case PICTURE_AREA -> GroupLabel.GROUP_LABEL_PICTURE_AREA;
            default -> GroupLabel.GROUP_LABEL_UNSPECIFIED;
        };
    }

    private static RefItem mapRefItem(DoclingDocument.RefItem javaRef) {
        RefItem.Builder builder = RefItem.newBuilder();

        if (javaRef.getRef() != null) {
            builder.setRef(javaRef.getRef());
        }

        return builder.build();
    }

    private static BaseTextItem mapBaseTextItem(DoclingDocument.BaseTextItem javaText) {
        BaseTextItem.Builder builder = BaseTextItem.newBuilder();

        // Use label field as type discriminator - 1:1 mapping to proto oneof
        DoclingDocument.DocItemLabel label = javaText.getLabel();

        switch (label) {
            case TITLE:
                if (javaText instanceof DoclingDocument.TitleItem titleItem) {
                    builder.setTitle(mapTitleItem(titleItem));
                }
                break;
            case SECTION_HEADER:
                if (javaText instanceof DoclingDocument.SectionHeaderItem sectionItem) {
                    builder.setSectionHeader(mapSectionHeaderItem(sectionItem));
                }
                break;
            case LIST_ITEM:
                if (javaText instanceof DoclingDocument.ListItem listItem) {
                    builder.setListItem(mapListItem(listItem));
                }
                break;
            case CODE:
                if (javaText instanceof DoclingDocument.CodeItem codeItem) {
                    builder.setCode(mapCodeItem(codeItem));
                }
                break;
            case FORMULA:
                if (javaText instanceof DoclingDocument.FormulaItem formulaItem) {
                    builder.setFormula(mapFormulaItem(formulaItem));
                }
                break;
            case TEXT:
            case PARAGRAPH:
            case CAPTION:
            case FOOTNOTE:
            case PAGE_HEADER:
            case PAGE_FOOTER:
            case REFERENCE:
            case CHECKBOX_SELECTED:
            case CHECKBOX_UNSELECTED:
            case EMPTY_VALUE:
                // All these map to TextItem in Java (see @JsonSubTypes)
                if (javaText instanceof DoclingDocument.TextItem textItem) {
                    builder.setText(mapTextItem(textItem));
                }
                break;
            default:
                LOG.warnf("Unknown BaseTextItem label: %s", label);
        }

        return builder.build();
    }

    private static TitleItem mapTitleItem(DoclingDocument.TitleItem javaTitle) {
        TitleItem.Builder builder = TitleItem.newBuilder();

        // Map TextItemBase fields
        builder.setBase(mapTextItemBase(javaTitle));

        return builder.build();
    }

    private static SectionHeaderItem mapSectionHeaderItem(DoclingDocument.SectionHeaderItem javaSection) {
        SectionHeaderItem.Builder builder = SectionHeaderItem.newBuilder();

        // Map TextItemBase fields
        builder.setBase(mapTextItemBase(javaSection));

        // Map level field specific to section headers
        if (javaSection.getLevel() != null) {
            builder.setLevel(javaSection.getLevel());
        }

        return builder.build();
    }

    private static ListItem mapListItem(DoclingDocument.ListItem javaList) {
        ListItem.Builder builder = ListItem.newBuilder();

        // Map TextItemBase fields
        builder.setBase(mapTextItemBase(javaList));

        // Map list-specific fields
        builder.setEnumerated(javaList.isEnumerated());
        if (javaList.getMarker() != null) {
            builder.setMarker(javaList.getMarker());
        }

        return builder.build();
    }

    private static CodeItem mapCodeItem(DoclingDocument.CodeItem javaCode) {
        CodeItem.Builder builder = CodeItem.newBuilder();

        // Map TextItemBase fields
        builder.setBase(mapTextItemBase(javaCode));

        // Map code-specific fields
        if (javaCode.getCodeLanguage() != null) {
            builder.setCodeLanguage(javaCode.getCodeLanguage());
        }

        // Map additional code item fields
        if (javaCode.getCaptions() != null) {
            javaCode.getCaptions().forEach(caption -> builder.addCaptions(mapRefItem(caption)));
        }
        if (javaCode.getReferences() != null) {
            javaCode.getReferences().forEach(ref -> builder.addReferences(mapRefItem(ref)));
        }
        if (javaCode.getFootnotes() != null) {
            javaCode.getFootnotes().forEach(footnote -> builder.addFootnotes(mapRefItem(footnote)));
        }
        if (javaCode.getImage() != null) {
            builder.setImage(mapImageRef(javaCode.getImage()));
        }

        return builder.build();
    }

    private static FormulaItem mapFormulaItem(DoclingDocument.FormulaItem javaFormula) {
        FormulaItem.Builder builder = FormulaItem.newBuilder();

        // Map TextItemBase fields
        builder.setBase(mapTextItemBase(javaFormula));

        return builder.build();
    }

    private static TextItem mapTextItem(DoclingDocument.TextItem javaText) {
        TextItem.Builder builder = TextItem.newBuilder();

        // Map TextItemBase fields
        builder.setBase(mapTextItemBase(javaText));

        return builder.build();
    }

    private static TextItemBase mapTextItemBase(DoclingDocument.BaseTextItem javaItem) {
        TextItemBase.Builder builder = TextItemBase.newBuilder();

        if (javaItem.getSelfRef() != null) {
            builder.setSelfRef(javaItem.getSelfRef());
        }
        if (javaItem.getParent() != null) {
            builder.setParent(mapRefItem(javaItem.getParent()));
        }
        if (javaItem.getChildren() != null) {
            javaItem.getChildren().forEach(child -> builder.addChildren(mapRefItem(child)));
        }

        // Map content_layer enum
        if (javaItem.getContentLayer() != null) {
            builder.setContentLayer(mapContentLayer(javaItem.getContentLayer()));
        }

        // Map label enum
        if (javaItem.getLabel() != null) {
            builder.setLabel(mapDocItemLabel(javaItem.getLabel()));
        }

        // Map provenance items
        if (javaItem.getProv() != null) {
            javaItem.getProv().forEach(prov -> builder.addProv(mapProvenanceItem(prov)));
        }

        if (javaItem.getOrig() != null) {
            builder.setOrig(javaItem.getOrig());
        }
        if (javaItem.getText() != null) {
            builder.setText(javaItem.getText());
        }
        if (javaItem.getFormatting() != null) {
            builder.setFormatting(mapFormatting(javaItem.getFormatting()));
        }
        if (javaItem.getHyperlink() != null) {
            builder.setHyperlink(javaItem.getHyperlink());
        }

        return builder.build();
    }

    private static ContentLayer mapContentLayer(DoclingDocument.ContentLayer javaLayer) {
        return switch (javaLayer) {
            case BODY -> ContentLayer.CONTENT_LAYER_BODY;
            case FURNITURE -> ContentLayer.CONTENT_LAYER_FURNITURE;
            case BACKGROUND -> ContentLayer.CONTENT_LAYER_BACKGROUND;
            case INVISIBLE -> ContentLayer.CONTENT_LAYER_INVISIBLE;
            case NOTES -> ContentLayer.CONTENT_LAYER_NOTES;
            default -> ContentLayer.CONTENT_LAYER_UNSPECIFIED;
        };
    }

    private static DocItemLabel mapDocItemLabel(DoclingDocument.DocItemLabel javaLabel) {
        return switch (javaLabel) {
            case CAPTION -> DocItemLabel.DOC_ITEM_LABEL_CAPTION;
            case CHART -> DocItemLabel.DOC_ITEM_LABEL_CHART;
            case CHECKBOX_SELECTED -> DocItemLabel.DOC_ITEM_LABEL_CHECKBOX_SELECTED;
            case CHECKBOX_UNSELECTED -> DocItemLabel.DOC_ITEM_LABEL_CHECKBOX_UNSELECTED;
            case CODE -> DocItemLabel.DOC_ITEM_LABEL_CODE;
            case DOCUMENT_INDEX -> DocItemLabel.DOC_ITEM_LABEL_DOCUMENT_INDEX;
            case EMPTY_VALUE -> DocItemLabel.DOC_ITEM_LABEL_EMPTY_VALUE;
            case FOOTNOTE -> DocItemLabel.DOC_ITEM_LABEL_FOOTNOTE;
            case FORM -> DocItemLabel.DOC_ITEM_LABEL_FORM;
            case FORMULA -> DocItemLabel.DOC_ITEM_LABEL_FORMULA;
            case GRADING_SCALE -> DocItemLabel.DOC_ITEM_LABEL_GRADING_SCALE;
            case HANDWRITTEN_TEXT -> DocItemLabel.DOC_ITEM_LABEL_HANDWRITTEN_TEXT;
            case KEY_VALUE_REGION -> DocItemLabel.DOC_ITEM_LABEL_KEY_VALUE_REGION;
            case LIST_ITEM -> DocItemLabel.DOC_ITEM_LABEL_LIST_ITEM;
            case PAGE_FOOTER -> DocItemLabel.DOC_ITEM_LABEL_PAGE_FOOTER;
            case PAGE_HEADER -> DocItemLabel.DOC_ITEM_LABEL_PAGE_HEADER;
            case PARAGRAPH -> DocItemLabel.DOC_ITEM_LABEL_PARAGRAPH;
            case PICTURE -> DocItemLabel.DOC_ITEM_LABEL_PICTURE;
            case REFERENCE -> DocItemLabel.DOC_ITEM_LABEL_REFERENCE;
            case SECTION_HEADER -> DocItemLabel.DOC_ITEM_LABEL_SECTION_HEADER;
            case TABLE -> DocItemLabel.DOC_ITEM_LABEL_TABLE;
            case TEXT -> DocItemLabel.DOC_ITEM_LABEL_TEXT;
            case TITLE -> DocItemLabel.DOC_ITEM_LABEL_TITLE;
            default -> DocItemLabel.DOC_ITEM_LABEL_UNSPECIFIED;
        };
    }

    private static ProvenanceItem mapProvenanceItem(DoclingDocument.ProvenanceItem javaProv) {
        ProvenanceItem.Builder builder = ProvenanceItem.newBuilder();

        try {
            if (javaProv.getPageNo() != null) {
                builder.setPageNo(javaProv.getPageNo());
            }
            if (javaProv.getBbox() != null) {
                builder.setBbox(mapBoundingBox(javaProv.getBbox()));
            }
            if (javaProv.getCharspan() != null) {
                javaProv.getCharspan().forEach(builder::addCharspan);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error mapping ProvenanceItem - page_no=%s, bbox=%s",
                javaProv.getPageNo(), javaProv.getBbox());
            // Continue with partial data - return what we successfully mapped
        }

        return builder.build();
    }

    private static Formatting mapFormatting(DoclingDocument.Formatting javaFormatting) {
        Formatting.Builder builder = Formatting.newBuilder();

        try {
            builder.setBold(javaFormatting.isBold());
            builder.setItalic(javaFormatting.isItalic());
            builder.setUnderline(javaFormatting.isUnderline());
            builder.setStrikethrough(javaFormatting.isStrikethrough());

            if (javaFormatting.getScript() != null) {
                builder.setScript(mapScript(javaFormatting.getScript()));
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error mapping Formatting - bold=%s, italic=%s, script=%s",
                javaFormatting.isBold(), javaFormatting.isItalic(), javaFormatting.getScript());
            // Continue with partial data
        }

        return builder.build();
    }

    private static Script mapScript(DoclingDocument.Script javaScript) {
        return switch (javaScript) {
            case BASELINE -> Script.SCRIPT_BASELINE;
            case SUB -> Script.SCRIPT_SUB;
            case SUPER -> Script.SCRIPT_SUPER;
            default -> Script.SCRIPT_UNSPECIFIED;
        };
    }

    private static PictureItem mapPictureItem(DoclingDocument.PictureItem javaPicture) {
        PictureItem.Builder builder = PictureItem.newBuilder();

        try {
            if (javaPicture.getSelfRef() != null) {
                builder.setSelfRef(javaPicture.getSelfRef());
            }
            if (javaPicture.getParent() != null) {
                builder.setParent(mapRefItem(javaPicture.getParent()));
            }
            if (javaPicture.getChildren() != null) {
                javaPicture.getChildren().forEach(child -> builder.addChildren(mapRefItem(child)));
            }
            if (javaPicture.getContentLayer() != null) {
                builder.setContentLayer(mapContentLayer(javaPicture.getContentLayer()));
            }
            if (javaPicture.getMeta() != null) {
                builder.setMeta(mapPictureMeta(javaPicture.getMeta()));
            }
            if (javaPicture.getLabel() != null) {
                builder.setLabel(javaPicture.getLabel());
            }
            if (javaPicture.getProv() != null) {
                javaPicture.getProv().forEach(prov -> builder.addProv(mapProvenanceItem(prov)));
            }
            if (javaPicture.getCaptions() != null) {
                javaPicture.getCaptions().forEach(caption -> builder.addCaptions(mapRefItem(caption)));
            }
            if (javaPicture.getReferences() != null) {
                javaPicture.getReferences().forEach(ref -> builder.addReferences(mapRefItem(ref)));
            }
            if (javaPicture.getFootnotes() != null) {
                javaPicture.getFootnotes().forEach(footnote -> builder.addFootnotes(mapRefItem(footnote)));
            }
            if (javaPicture.getImage() != null) {
                builder.setImage(mapImageRef(javaPicture.getImage()));
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error mapping PictureItem - selfRef=%s, label=%s",
                javaPicture.getSelfRef(), javaPicture.getLabel());
            // Continue with partial data
        }

        return builder.build();
    }

    private static PictureMeta mapPictureMeta(DoclingDocument.PictureMeta javaMeta) {
        PictureMeta.Builder builder = PictureMeta.newBuilder();

        try {
            if (javaMeta.getSummary() != null) {
                builder.setSummary(mapSummaryMetaField(javaMeta.getSummary()));
            }
            if (javaMeta.getDescription() != null) {
                builder.setDescription(mapDescriptionMetaField(javaMeta.getDescription()));
            }
            if (javaMeta.getClassification() != null) {
                builder.setClassification(mapPictureClassificationMetaField(javaMeta.getClassification()));
            }
            if (javaMeta.getMolecule() != null) {
                builder.setMolecule(mapMoleculeMetaField(javaMeta.getMolecule()));
            }
            if (javaMeta.getTabularChart() != null) {
                builder.setTabularChart(mapTabularChartMetaField(javaMeta.getTabularChart()));
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error mapping PictureMeta");
            // Continue with partial data
        }

        return builder.build();
    }

    private static SummaryMetaField mapSummaryMetaField(DoclingDocument.SummaryMetaField javaSummary) {
        SummaryMetaField.Builder builder = SummaryMetaField.newBuilder();

        if (javaSummary.getConfidence() != null) {
            builder.setConfidence(javaSummary.getConfidence());
        }
        if (javaSummary.getCreatedBy() != null) {
            builder.setCreatedBy(javaSummary.getCreatedBy());
        }
        if (javaSummary.getText() != null) {
            builder.setText(javaSummary.getText());
        }

        return builder.build();
    }

    private static DescriptionMetaField mapDescriptionMetaField(DoclingDocument.DescriptionMetaField javaDesc) {
        DescriptionMetaField.Builder builder = DescriptionMetaField.newBuilder();

        if (javaDesc.getConfidence() != null) {
            builder.setConfidence(javaDesc.getConfidence());
        }
        if (javaDesc.getCreatedBy() != null) {
            builder.setCreatedBy(javaDesc.getCreatedBy());
        }
        if (javaDesc.getText() != null) {
            builder.setText(javaDesc.getText());
        }

        return builder.build();
    }

    private static PictureClassificationMetaField mapPictureClassificationMetaField(
            DoclingDocument.PictureClassificationMetaField javaClassification) {
        PictureClassificationMetaField.Builder builder = PictureClassificationMetaField.newBuilder();

        if (javaClassification.getPredictions() != null) {
            javaClassification.getPredictions().forEach(pred ->
                builder.addPredictions(mapPictureClassificationPrediction(pred)));
        }

        return builder.build();
    }

    private static PictureClassificationPrediction mapPictureClassificationPrediction(
            DoclingDocument.PictureClassificationPrediction javaPred) {
        PictureClassificationPrediction.Builder builder = PictureClassificationPrediction.newBuilder();

        if (javaPred.getConfidence() != null) {
            builder.setConfidence(javaPred.getConfidence());
        }
        if (javaPred.getCreatedBy() != null) {
            builder.setCreatedBy(javaPred.getCreatedBy());
        }
        if (javaPred.getClassName() != null) {
            builder.setClassName(javaPred.getClassName());
        }

        return builder.build();
    }

    private static MoleculeMetaField mapMoleculeMetaField(DoclingDocument.MoleculeMetaField javaMol) {
        MoleculeMetaField.Builder builder = MoleculeMetaField.newBuilder();

        if (javaMol.getConfidence() != null) {
            builder.setConfidence(javaMol.getConfidence());
        }
        if (javaMol.getCreatedBy() != null) {
            builder.setCreatedBy(javaMol.getCreatedBy());
        }
        if (javaMol.getSmi() != null) {
            builder.setSmi(javaMol.getSmi());
        }

        return builder.build();
    }

    private static TabularChartMetaField mapTabularChartMetaField(DoclingDocument.TabularChartMetaField javaChart) {
        TabularChartMetaField.Builder builder = TabularChartMetaField.newBuilder();

        if (javaChart.getConfidence() != null) {
            builder.setConfidence(javaChart.getConfidence());
        }
        if (javaChart.getCreatedBy() != null) {
            builder.setCreatedBy(javaChart.getCreatedBy());
        }
        if (javaChart.getTitle() != null) {
            builder.setTitle(javaChart.getTitle());
        }
        if (javaChart.getChartData() != null) {
            builder.setChartData(mapTableData(javaChart.getChartData()));
        }

        return builder.build();
    }

    private static TableItem mapTableItem(DoclingDocument.TableItem javaTable) {
        TableItem.Builder builder = TableItem.newBuilder();

        try {
            if (javaTable.getSelfRef() != null) {
                builder.setSelfRef(javaTable.getSelfRef());
            }
            if (javaTable.getParent() != null) {
                builder.setParent(mapRefItem(javaTable.getParent()));
            }
            if (javaTable.getChildren() != null) {
                javaTable.getChildren().forEach(child -> builder.addChildren(mapRefItem(child)));
            }
            if (javaTable.getContentLayer() != null) {
                builder.setContentLayer(mapContentLayer(javaTable.getContentLayer()));
            }
            if (javaTable.getMeta() != null) {
                builder.setMeta(mapFloatingMeta(javaTable.getMeta()));
            }
            if (javaTable.getLabel() != null) {
                builder.setLabel(javaTable.getLabel());
            }
            if (javaTable.getProv() != null) {
                javaTable.getProv().forEach(prov -> builder.addProv(mapProvenanceItem(prov)));
            }
            if (javaTable.getCaptions() != null) {
                javaTable.getCaptions().forEach(caption -> builder.addCaptions(mapRefItem(caption)));
            }
            if (javaTable.getReferences() != null) {
                javaTable.getReferences().forEach(ref -> builder.addReferences(mapRefItem(ref)));
            }
            if (javaTable.getFootnotes() != null) {
                javaTable.getFootnotes().forEach(footnote -> builder.addFootnotes(mapRefItem(footnote)));
            }
            if (javaTable.getImage() != null) {
                builder.setImage(mapImageRef(javaTable.getImage()));
            }
            if (javaTable.getData() != null) {
                builder.setData(mapTableData(javaTable.getData()));
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error mapping TableItem - selfRef=%s, label=%s",
                javaTable.getSelfRef(), javaTable.getLabel());
            // Continue with partial data
        }

        return builder.build();
    }

    private static TableData mapTableData(DoclingDocument.TableData javaTableData) {
        TableData.Builder builder = TableData.newBuilder();

        // Skip table_cells for now - it's List<Object> which is polymorphic
        // The structured data is in grid which we map below
        // TODO: Handle table_cells polymorphism if needed

        if (javaTableData.getNumRows() != null) {
            builder.setNumRows(javaTableData.getNumRows());
        }
        if (javaTableData.getNumCols() != null) {
            builder.setNumCols(javaTableData.getNumCols());
        }

        // Map grid - it's List<List<TableCell>> in Java, need to wrap in TableRow for proto
        if (javaTableData.getGrid() != null) {
            javaTableData.getGrid().forEach(cellList -> {
                TableRow.Builder rowBuilder = TableRow.newBuilder();
                cellList.forEach(cell -> rowBuilder.addCells(mapTableCell(cell)));
                builder.addGrid(rowBuilder.build());
            });
        }

        return builder.build();
    }

    private static TableCell mapTableCell(DoclingDocument.TableCell javaCell) {
        TableCell.Builder builder = TableCell.newBuilder();

        if (javaCell.getBbox() != null) {
            builder.setBbox(mapBoundingBox(javaCell.getBbox()));
        }

        if (javaCell.getRowSpan() != null) {
            builder.setRowSpan(javaCell.getRowSpan());
        }
        if (javaCell.getColSpan() != null) {
            builder.setColSpan(javaCell.getColSpan());
        }
        if (javaCell.getStartRowOffsetIdx() != null) {
            builder.setStartRowOffsetIdx(javaCell.getStartRowOffsetIdx());
        }
        if (javaCell.getEndRowOffsetIdx() != null) {
            builder.setEndRowOffsetIdx(javaCell.getEndRowOffsetIdx());
        }
        if (javaCell.getStartColOffsetIdx() != null) {
            builder.setStartColOffsetIdx(javaCell.getStartColOffsetIdx());
        }
        if (javaCell.getEndColOffsetIdx() != null) {
            builder.setEndColOffsetIdx(javaCell.getEndColOffsetIdx());
        }

        if (javaCell.getText() != null) {
            builder.setText(javaCell.getText());
        }

        builder.setColumnHeader(javaCell.isColumnHeader());
        builder.setRowHeader(javaCell.isRowHeader());
        builder.setRowSection(javaCell.isRowSection());
        builder.setFillable(javaCell.isFillable());

        return builder.build();
    }

    private static BoundingBox mapBoundingBox(DoclingDocument.BoundingBox javaBbox) {
        BoundingBox.Builder builder = BoundingBox.newBuilder();

        if (javaBbox.getL() != null) {
            builder.setL(javaBbox.getL());
        }
        if (javaBbox.getT() != null) {
            builder.setT(javaBbox.getT());
        }
        if (javaBbox.getR() != null) {
            builder.setR(javaBbox.getR());
        }
        if (javaBbox.getB() != null) {
            builder.setB(javaBbox.getB());
        }
        // No coord field in Java BoundingBox

        return builder.build();
    }

    private static KeyValueItem mapKeyValueItem(DoclingDocument.KeyValueItem javaKv) {
        KeyValueItem.Builder builder = KeyValueItem.newBuilder();

        try {
            if (javaKv.getSelfRef() != null) {
                builder.setSelfRef(javaKv.getSelfRef());
            }
            if (javaKv.getParent() != null) {
                builder.setParent(mapRefItem(javaKv.getParent()));
            }
            if (javaKv.getChildren() != null) {
                javaKv.getChildren().forEach(child -> builder.addChildren(mapRefItem(child)));
            }
            if (javaKv.getContentLayer() != null) {
                builder.setContentLayer(mapContentLayer(javaKv.getContentLayer()));
            }
            if (javaKv.getMeta() != null) {
                builder.setMeta(mapFloatingMeta(javaKv.getMeta()));
            }
            if (javaKv.getLabel() != null) {
                builder.setLabel(javaKv.getLabel());
            }
            if (javaKv.getProv() != null) {
                javaKv.getProv().forEach(prov -> builder.addProv(mapProvenanceItem(prov)));
            }
            if (javaKv.getCaptions() != null) {
                javaKv.getCaptions().forEach(caption -> builder.addCaptions(mapRefItem(caption)));
            }
            if (javaKv.getReferences() != null) {
                javaKv.getReferences().forEach(ref -> builder.addReferences(mapRefItem(ref)));
            }
            if (javaKv.getFootnotes() != null) {
                javaKv.getFootnotes().forEach(footnote -> builder.addFootnotes(mapRefItem(footnote)));
            }
            if (javaKv.getImage() != null) {
                builder.setImage(mapImageRef(javaKv.getImage()));
            }
            if (javaKv.getGraph() != null) {
                builder.setGraph(mapGraphData(javaKv.getGraph()));
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error mapping KeyValueItem - selfRef=%s, label=%s",
                javaKv.getSelfRef(), javaKv.getLabel());
            // Continue with partial data
        }

        return builder.build();
    }

    private static FormItem mapFormItem(DoclingDocument.FormItem javaForm) {
        FormItem.Builder builder = FormItem.newBuilder();

        try {
            if (javaForm.getSelfRef() != null) {
                builder.setSelfRef(javaForm.getSelfRef());
            }
            if (javaForm.getParent() != null) {
                builder.setParent(mapRefItem(javaForm.getParent()));
            }
            if (javaForm.getChildren() != null) {
                javaForm.getChildren().forEach(child -> builder.addChildren(mapRefItem(child)));
            }
            if (javaForm.getContentLayer() != null) {
                builder.setContentLayer(mapContentLayer(javaForm.getContentLayer()));
            }
            if (javaForm.getMeta() != null) {
                builder.setMeta(mapFloatingMeta(javaForm.getMeta()));
            }
            if (javaForm.getLabel() != null) {
                builder.setLabel(javaForm.getLabel());
            }
            if (javaForm.getProv() != null) {
                javaForm.getProv().forEach(prov -> builder.addProv(mapProvenanceItem(prov)));
            }
            if (javaForm.getCaptions() != null) {
                javaForm.getCaptions().forEach(caption -> builder.addCaptions(mapRefItem(caption)));
            }
            if (javaForm.getReferences() != null) {
                javaForm.getReferences().forEach(ref -> builder.addReferences(mapRefItem(ref)));
            }
            if (javaForm.getFootnotes() != null) {
                javaForm.getFootnotes().forEach(footnote -> builder.addFootnotes(mapRefItem(footnote)));
            }
            if (javaForm.getImage() != null) {
                builder.setImage(mapImageRef(javaForm.getImage()));
            }
            if (javaForm.getGraph() != null) {
                builder.setGraph(mapGraphData(javaForm.getGraph()));
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error mapping FormItem - selfRef=%s, label=%s",
                javaForm.getSelfRef(), javaForm.getLabel());
            // Continue with partial data
        }

        return builder.build();
    }

    private static FloatingMeta mapFloatingMeta(DoclingDocument.FloatingMeta javaMeta) {
        FloatingMeta.Builder builder = FloatingMeta.newBuilder();

        if (javaMeta.getSummary() != null) {
            builder.setSummary(mapSummaryMetaField(javaMeta.getSummary()));
        }
        if (javaMeta.getDescription() != null) {
            builder.setDescription(mapDescriptionMetaField(javaMeta.getDescription()));
        }

        return builder.build();
    }

    private static GraphData mapGraphData(DoclingDocument.GraphData javaGraph) {
        GraphData.Builder builder = GraphData.newBuilder();

        try {
            if (javaGraph.getCells() != null) {
                javaGraph.getCells().forEach(cell -> builder.addCells(mapGraphCell(cell)));
            }
            if (javaGraph.getLinks() != null) {
                javaGraph.getLinks().forEach(link -> builder.addLinks(mapGraphLink(link)));
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error mapping GraphData - cells count=%s, links count=%s",
                javaGraph.getCells() != null ? javaGraph.getCells().size() : 0,
                javaGraph.getLinks() != null ? javaGraph.getLinks().size() : 0);
            // Continue with partial data
        }

        return builder.build();
    }

    private static GraphCell mapGraphCell(DoclingDocument.GraphCell javaCell) {
        GraphCell.Builder builder = GraphCell.newBuilder();

        if (javaCell.getLabel() != null) {
            builder.setLabel(mapGraphCellLabel(javaCell.getLabel()));
        }
        if (javaCell.getCellId() != null) {
            builder.setCellId(javaCell.getCellId());
        }
        if (javaCell.getText() != null) {
            builder.setText(javaCell.getText());
        }
        if (javaCell.getOrig() != null) {
            builder.setOrig(javaCell.getOrig());
        }
        if (javaCell.getProv() != null) {
            builder.setProv(mapProvenanceItem(javaCell.getProv()));
        }
        if (javaCell.getItemRef() != null) {
            builder.setItemRef(mapRefItem(javaCell.getItemRef()));
        }

        return builder.build();
    }

    private static GraphCellLabel mapGraphCellLabel(DoclingDocument.GraphCellLabel javaLabel) {
        return switch (javaLabel) {
            case KEY -> GraphCellLabel.GRAPH_CELL_LABEL_KEY;
            case VALUE -> GraphCellLabel.GRAPH_CELL_LABEL_VALUE;
            case CHECKBOX -> GraphCellLabel.GRAPH_CELL_LABEL_CHECKBOX;
            default -> GraphCellLabel.GRAPH_CELL_LABEL_UNSPECIFIED;
        };
    }

    private static GraphLink mapGraphLink(DoclingDocument.GraphLink javaLink) {
        GraphLink.Builder builder = GraphLink.newBuilder();

        if (javaLink.getLabel() != null) {
            builder.setLabel(mapGraphLinkLabel(javaLink.getLabel()));
        }
        if (javaLink.getSourceCellId() != null) {
            builder.setSourceCellId(javaLink.getSourceCellId());
        }
        if (javaLink.getTargetCellId() != null) {
            builder.setTargetCellId(javaLink.getTargetCellId());
        }

        return builder.build();
    }

    private static GraphLinkLabel mapGraphLinkLabel(DoclingDocument.GraphLinkLabel javaLabel) {
        return switch (javaLabel) {
            case TO_VALUE -> GraphLinkLabel.GRAPH_LINK_LABEL_TO_VALUE;
            case TO_KEY -> GraphLinkLabel.GRAPH_LINK_LABEL_TO_KEY;
            case TO_PARENT -> GraphLinkLabel.GRAPH_LINK_LABEL_TO_PARENT;
            case TO_CHILD -> GraphLinkLabel.GRAPH_LINK_LABEL_TO_CHILD;
            default -> GraphLinkLabel.GRAPH_LINK_LABEL_UNSPECIFIED;
        };
    }

    private static PageItem mapPageItem(DoclingDocument.PageItem javaPage) {
        PageItem.Builder builder = PageItem.newBuilder();

        if (javaPage.getSize() != null) {
            builder.setSize(mapSize(javaPage.getSize()));
        }
        if (javaPage.getImage() != null) {
            builder.setImage(mapImageRef(javaPage.getImage()));
        }
        builder.setPageNo(javaPage.getPageNo());

        return builder.build();
    }

    private static Size mapSize(DoclingDocument.Size javaSize) {
        Size.Builder builder = Size.newBuilder();

        builder.setWidth(javaSize.getWidth());
        builder.setHeight(javaSize.getHeight());

        return builder.build();
    }

    private static ImageRef mapImageRef(DoclingDocument.ImageRef javaImageRef) {
        ImageRef.Builder builder = ImageRef.newBuilder();

        try {
            if (javaImageRef.getMimetype() != null) {
                builder.setMimetype(javaImageRef.getMimetype());
            }
            if (javaImageRef.getDpi() != null) {
                builder.setDpi(javaImageRef.getDpi());
            }
            if (javaImageRef.getSize() != null) {
                builder.setSize(mapSize(javaImageRef.getSize()));
            }
            if (javaImageRef.getUri() != null) {
                builder.setUri(javaImageRef.getUri());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error mapping ImageRef - mimetype=%s, dpi=%s, uri=%s",
                javaImageRef.getMimetype(), javaImageRef.getDpi(), javaImageRef.getUri());
            // Continue with partial data
        }

        return builder.build();
    }
}
