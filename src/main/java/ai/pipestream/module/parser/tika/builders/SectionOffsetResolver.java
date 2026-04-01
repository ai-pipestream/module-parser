package ai.pipestream.module.parser.tika.builders;

import ai.pipestream.data.v1.DocOutline;
import ai.pipestream.data.v1.Section;

/**
 * Post-processes a DocOutline to resolve character offsets for each section
 * by finding heading text positions in the extracted body text.
 * <p>
 * This is document-type agnostic — works for HTML, Markdown, PDF, EPUB, etc.
 * because the extracted body text always contains the heading text.
 * <p>
 * Character offsets enable section-level vector centroids and search result highlighting.
 */
public final class SectionOffsetResolver {

    private SectionOffsetResolver() {}

    /**
     * Resolves char_start_offset and char_end_offset for each section in the outline
     * by finding the section title's position in the extracted body text.
     * <p>
     * Each section's start offset is where its heading text appears in the body.
     * Each section's end offset is the start of the next section (or end of text).
     * <p>
     * Sections whose titles can't be found in the body text are left without offsets.
     *
     * @param outline  the DocOutline with sections (titles must be populated)
     * @param bodyText the extracted text content (from Tika or Docling)
     * @return new DocOutline with char_start_offset and char_end_offset populated
     */
    public static DocOutline resolve(DocOutline outline, String bodyText) {
        if (outline == null || outline.getSectionsCount() == 0
                || bodyText == null || bodyText.isEmpty()) {
            return outline;
        }

        // First pass: find each section's heading position in the body text
        int sectionCount = outline.getSectionsCount();
        int[] startOffsets = new int[sectionCount];
        boolean[] found = new boolean[sectionCount];
        int searchFrom = 0;

        for (int i = 0; i < sectionCount; i++) {
            Section section = outline.getSections(i);
            String title = section.hasTitle() ? section.getTitle().trim() : "";

            if (title.isEmpty()) {
                startOffsets[i] = -1;
                found[i] = false;
                continue;
            }

            // Search forward from last found position to maintain document order
            int pos = bodyText.indexOf(title, searchFrom);
            if (pos >= 0) {
                startOffsets[i] = pos;
                found[i] = true;
                searchFrom = pos + title.length();
            } else {
                // Retry case-insensitive from searchFrom
                pos = indexOfIgnoreCase(bodyText, title, searchFrom);
                if (pos >= 0) {
                    startOffsets[i] = pos;
                    found[i] = true;
                    searchFrom = pos + title.length();
                } else {
                    startOffsets[i] = -1;
                    found[i] = false;
                }
            }
        }

        // Second pass: build new outline with offsets
        // Each section ends where the next found section begins (or at end of text)
        DocOutline.Builder newOutline = DocOutline.newBuilder();

        for (int i = 0; i < sectionCount; i++) {
            Section.Builder sb = outline.getSections(i).toBuilder();

            if (found[i]) {
                sb.setCharStartOffset(startOffsets[i]);

                // End offset: find the next section that was found
                int endOffset = bodyText.length();
                for (int j = i + 1; j < sectionCount; j++) {
                    if (found[j]) {
                        endOffset = startOffsets[j];
                        break;
                    }
                }
                sb.setCharEndOffset(endOffset);
            }

            newOutline.addSections(sb.build());
        }

        return newOutline.build();
    }

    private static int indexOfIgnoreCase(String text, String target, int from) {
        String lowerText = text.toLowerCase();
        String lowerTarget = target.toLowerCase();
        return lowerText.indexOf(lowerTarget, from);
    }
}
