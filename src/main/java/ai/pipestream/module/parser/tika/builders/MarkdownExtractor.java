package ai.pipestream.module.parser.tika.builders;

import ai.pipestream.data.v1.DocOutline;
import ai.pipestream.data.v1.LinkReference;
import ai.pipestream.data.v1.Section;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

import java.nio.charset.Charset;
import java.util.*;

/**
 * Extracts headings and links from Markdown using CommonMark.
 */
public final class MarkdownExtractor {

    private MarkdownExtractor() {}

    public static DocOutline buildDocOutlineFromMarkdown(byte[] markdownBytes,
                                                         int minHeadingLevel,
                                                         int maxHeadingLevel,
                                                         boolean generateIds) {
        if (markdownBytes == null || markdownBytes.length == 0) {
            return DocOutline.newBuilder().build();
        }
        String md = new String(markdownBytes, Charset.forName("UTF-8"));
        Parser parser = Parser.builder().build();
        Node document = parser.parse(md);

        DocOutline.Builder outline = DocOutline.newBuilder();
        Map<Integer, String> lastSectionIdAtLevel = new HashMap<>();
        int[] orderCounter = new int[]{0};

        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {
                int level = heading.getLevel();
                if (level < minHeadingLevel || level > maxHeadingLevel) {
                    visitChildren(heading);
                    return;
                }
                String title = extractText(heading);
                String generatedId = "sec-" + orderCounter[0];
                String id = generateIds ? generatedId : "";

                // Find parent id from last seen heading with smaller level
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
                        .addTags("h" + level);
                if (parentId != null) sb.setParentId(parentId);

                outline.addSections(sb.build());

                lastSectionIdAtLevel.put(level, id);
                for (int l = level + 1; l <= 6; l++) lastSectionIdAtLevel.remove(l);

                visitChildren(heading);
            }
        });

        return outline.build();
    }

    public static List<LinkReference> extractLinks(byte[] markdownBytes, String baseUri) {
        List<LinkReference> out = new ArrayList<>();
        if (markdownBytes == null || markdownBytes.length == 0) return out;
        String md = new String(markdownBytes, Charset.forName("UTF-8"));
        Parser parser = Parser.builder().build();
        Node document = parser.parse(md);

        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {
                String url = link.getDestination();
                String text = extractText(link);
                LinkReference.Builder lb = LinkReference.newBuilder().setUrl(url == null ? "" : url);
                if (text != null && !text.isEmpty()) lb.setText(text);
                String title = link.getTitle();
                if (title != null && !title.isEmpty()) lb.setType(title); // repurpose title as type hint if present
                out.add(lb.build());
                visitChildren(link);
            }
        });
        // is_external determination (best effort) left to service when source_uri is available
        return out;
    }

    private static String extractText(Node node) {
        StringBuilder sb = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                sb.append(text.getLiteral());
            }

            @Override
            public void visit(SoftLineBreak softLineBreak) {
                sb.append(' ');
            }

            @Override
            public void visit(HardLineBreak hardLineBreak) {
                sb.append(' ');
            }

            @Override
            public void visit(Emphasis emphasis) { visitChildren(emphasis); }

            @Override
            public void visit(StrongEmphasis strongEmphasis) { visitChildren(strongEmphasis); }

            @Override
            public void visit(Link link) { visitChildren(link); }

            @Override
            public void visit(Image image) { /* skip */ }
        });
        return sb.toString().trim();
    }
}
