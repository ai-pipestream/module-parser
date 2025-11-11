package ai.pipestream.module.parser.tika.builders;

import ai.pipestream.data.v1.DocOutline;
import ai.pipestream.data.v1.Section;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a neutral DocOutline from an HTML document by reading headings (h1..h6)
 * in document order and constructing a hierarchy using heading levels.
 */
public final class HtmlOutlineExtractor {

    private HtmlOutlineExtractor() {}

    public static DocOutline buildDocOutlineFromHtml(byte[] htmlBytes) {
        return buildDocOutlineFromHtml(
                htmlBytes,
                null,   // includeCss
                null,   // excludeCss
                true,   // stripScripts
                1,      // minLevel
                6,      // maxLevel
                true    // generateIds
        );
    }

    public static DocOutline buildDocOutlineFromHtml(
            byte[] htmlBytes,
            String includeCss,
            String excludeCss,
            boolean stripScripts,
            int minHeadingLevel,
            int maxHeadingLevel,
            boolean generateIds
    ) {
        if (htmlBytes == null || htmlBytes.length == 0) {
            return DocOutline.newBuilder().build();
        }
        // Jsoup handles charset sniffing internally if charset is null
        Document doc = Jsoup.parse(new String(htmlBytes, Charset.forName("UTF-8")));
        if (stripScripts) {
            doc.select("script, noscript").remove();
        }
        // Optional include filter: narrow down to subtree(s)
        Document working = doc;
        if (includeCss != null && !includeCss.isBlank()) {
            // Create a shallow doc with the included elements cloned
            Document filtered = Document.createShell(doc.baseUri());
            for (Element el : doc.select(includeCss)) {
                filtered.body().appendChild(el.clone());
            }
            working = filtered;
        }
        // Optional exclude filter: remove matching elements
        if (excludeCss != null && !excludeCss.isBlank()) {
            working.select(excludeCss).remove();
        }
        DocOutline.Builder outline = DocOutline.newBuilder();

        // Track last seen heading at each level (1..6) to compute parents
        Map<Integer, String> lastSectionIdAtLevel = new HashMap<>();
        int[] orderCounter = new int[]{0};

        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (!(node instanceof Element)) return;
                Element el = (Element) node;
                String tag = el.tagName().toLowerCase();
                if (!tag.matches("h[1-6]")) return;
                int level = Character.getNumericValue(tag.charAt(1));
                if (level < minHeadingLevel || level > maxHeadingLevel) return;
                String title = el.text();
                String generatedId = "sec-" + orderCounter[0];
                String id = (el.id() != null && !el.id().isEmpty()) ? el.id() : (generateIds ? generatedId : "");
                String href = (el.id() != null && !el.id().isEmpty()) ? ("#" + el.id()) : null;

                // Find parent: closest previous heading with smaller level
                String parentId = null;
                for (int l = level - 1; l >= 1; l--) {
                    if (lastSectionIdAtLevel.containsKey(l)) {
                        parentId = lastSectionIdAtLevel.get(l);
                        break;
                    }
                }

                Section.Builder sb = Section.newBuilder()
                        .setId(id)
                        .setTitle(title)
                        .setDepth(level - 1)
                        .setHeadingLevel(level)
                        .setOrderIndex(orderCounter[0]++)
                        .addTags("heading")
                        .addTags(tag);
                if (href != null) sb.setHref(href);
                if (parentId != null) sb.setParentId(parentId);

                outline.addSections(sb.build());

                // Update last seen at this level and clear deeper levels
                lastSectionIdAtLevel.put(level, id);
                for (int l = level + 1; l <= 6; l++) {
                    lastSectionIdAtLevel.remove(l);
                }
            }

            @Override
            public void tail(Node node, int depth) {
                // no-op
            }
        }, doc);

        return outline.build();
    }

    public static java.util.List<ai.pipestream.data.v1.LinkReference> extractLinks(
            byte[] htmlBytes,
            String baseUri,
            boolean stripScripts,
            String includeCss,
            String excludeCss
    ) {
        java.util.List<ai.pipestream.data.v1.LinkReference> out = new java.util.ArrayList<>();
        if (htmlBytes == null || htmlBytes.length == 0) return out;
        Document doc = Jsoup.parse(new String(htmlBytes, Charset.forName("UTF-8")), baseUri);
        if (stripScripts) doc.select("script, noscript").remove();
        Document working = doc;
        if (includeCss != null && !includeCss.isBlank()) {
            Document filtered = Document.createShell(doc.baseUri());
            for (Element el : doc.select(includeCss)) filtered.body().appendChild(el.clone());
            working = filtered;
        }
        if (excludeCss != null && !excludeCss.isBlank()) {
            working.select(excludeCss).remove();
        }
        for (Element a : working.select("a[href]")) {
            String href = a.attr("href");
            String abs = a.hasAttr("href") ? a.absUrl("href") : null;
            String text = a.text();
            String rel = a.attr("rel");
            ai.pipestream.data.v1.LinkReference.Builder lb = ai.pipestream.data.v1.LinkReference.newBuilder()
                    .setUrl(abs != null && !abs.isEmpty() ? abs : href);
            if (text != null && !text.isEmpty()) lb.setText(text);
            if (rel != null && !rel.isEmpty()) lb.setRel(rel);
            out.add(lb.build());
        }
        return out;
    }
}
