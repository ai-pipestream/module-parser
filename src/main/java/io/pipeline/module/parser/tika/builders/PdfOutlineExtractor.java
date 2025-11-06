package io.pipeline.module.parser.tika.builders;

import io.pipeline.data.v1.DocOutline;
import io.pipeline.data.v1.Section;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import java.io.ByteArrayInputStream;

/**
 * Extracts PDF bookmarks/outlines using PDFBox and converts them to a neutral DocOutline.
 */
public final class PdfOutlineExtractor {

    private PdfOutlineExtractor() {}

    public static DocOutline buildDocOutlineFromPdf(byte[] pdfBytes) {
        DocOutline.Builder outline = DocOutline.newBuilder();
        if (pdfBytes == null || pdfBytes.length == 0) return outline.build();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDDocumentOutline root = doc.getDocumentCatalog().getDocumentOutline();
            if (root == null) return outline.build();
            int[] counter = new int[]{0};
            PDPageTree pages = doc.getPages();
            PDOutlineItem item = root.getFirstChild();
            while (item != null) {
                appendItem(outline, item, null, 0, counter, pages);
                item = item.getNextSibling();
            }
        } catch (Exception ignored) {}
        return outline.build();
    }

    private static void appendItem(DocOutline.Builder outline,
                                   PDOutlineItem item,
                                   String parentId,
                                   int depth,
                                   int[] counter,
                                   PDPageTree pages) {
        String id = "sec-" + counter[0];
        String title = item.getTitle();
        Section.Builder sb = Section.newBuilder()
                .setId(id)
                .setTitle(title == null ? "" : title)
                .setDepth(depth)
                .setOrderIndex(counter[0]++)
                .addTags("nav")
                .addTags("bookmark");
        // Page destination if available
        try {
            PDDestination dest = item.getDestination();
            if (dest instanceof PDPageDestination pdest) {
                int pageIndex = retrievePageIndex(pdest, pages);
                if (pageIndex >= 0) {
                    sb.setPageStart(pageIndex + 1);
                    sb.setHref("page=" + (pageIndex + 1));
                }
            }
        } catch (Exception ignored) {}
        if (parentId != null) sb.setParentId(parentId);
        outline.addSections(sb.build());

        // Children
        PDOutlineItem child = item.getFirstChild();
        while (child != null) {
            appendItem(outline, child, id, depth + 1, counter, pages);
            child = child.getNextSibling();
        }
    }

    private static int retrievePageIndex(PDPageDestination dest, PDPageTree pages) {
        try {
            int idx = dest.retrievePageNumber();
            if (idx >= 0) return idx;
        } catch (Exception ignored) {}
        try {
            PDPage page = dest.getPage();
            if (page != null) {
                int i = 0;
                for (PDPage p : pages) {
                    if (p == page) return i;
                    i++;
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
