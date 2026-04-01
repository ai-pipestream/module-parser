package ai.pipestream.module.parser.tika.builders;

import ai.pipestream.data.v1.DocOutline;
import ai.pipestream.data.v1.Section;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SectionOffsetResolverTest {

    @Test
    void resolve_findsHeadingsInBodyText() {
        String body = "Introduction\nThis is the intro paragraph.\n\nMethods\nWe did this and that.\n\nResults\nHere are the results.";

        DocOutline outline = DocOutline.newBuilder()
                .addSections(Section.newBuilder().setTitle("Introduction").setDepth(0).setOrderIndex(0))
                .addSections(Section.newBuilder().setTitle("Methods").setDepth(0).setOrderIndex(1))
                .addSections(Section.newBuilder().setTitle("Results").setDepth(0).setOrderIndex(2))
                .build();

        DocOutline resolved = SectionOffsetResolver.resolve(outline, body);

        assertEquals(3, resolved.getSectionsCount(), "Should preserve all 3 sections");

        Section intro = resolved.getSections(0);
        assertTrue(intro.hasCharStartOffset(), "Introduction should have start offset");
        assertEquals(0, intro.getCharStartOffset(), "Introduction starts at 0");

        Section methods = resolved.getSections(1);
        assertTrue(methods.hasCharStartOffset(), "Methods should have start offset");
        assertTrue(methods.getCharStartOffset() > 0, "Methods found after intro");

        // Intro ends where Methods begins
        assertEquals(methods.getCharStartOffset(), intro.getCharEndOffset(),
                "Intro ends where Methods begins");

        Section results = resolved.getSections(2);
        assertTrue(results.hasCharStartOffset(), "Results should have start offset");
        assertEquals(body.length(), results.getCharEndOffset(), "Results ends at body length");
    }

    @Test
    void resolve_preservesExistingFields() {
        String body = "Heading One\nSome text.\n\nHeading Two\nMore text.";

        DocOutline outline = DocOutline.newBuilder()
                .addSections(Section.newBuilder()
                        .setId("sec-0").setTitle("Heading One").setDepth(0)
                        .setOrderIndex(0).setHeadingLevel(1).addTags("heading"))
                .addSections(Section.newBuilder()
                        .setId("sec-1").setTitle("Heading Two").setDepth(0)
                        .setOrderIndex(1).setHeadingLevel(1).addTags("heading"))
                .build();

        DocOutline resolved = SectionOffsetResolver.resolve(outline, body);

        assertEquals("sec-0", resolved.getSections(0).getId(), "Preserves id");
        assertEquals(1, resolved.getSections(0).getHeadingLevel(), "Preserves heading level");
        assertEquals(1, resolved.getSections(0).getTagsCount(), "Preserves tags");
        assertTrue(resolved.getSections(0).hasCharStartOffset(), "Adds char offset");
    }

    @Test
    void resolve_emptyOutline_returnsAsIs() {
        DocOutline empty = DocOutline.newBuilder().build();
        DocOutline resolved = SectionOffsetResolver.resolve(empty, "some body text");
        assertEquals(0, resolved.getSectionsCount(), "Empty outline stays empty");
    }

    @Test
    void resolve_nullBody_returnsAsIs() {
        DocOutline outline = DocOutline.newBuilder()
                .addSections(Section.newBuilder().setTitle("Test").setDepth(0))
                .build();

        DocOutline resolved = SectionOffsetResolver.resolve(outline, null);
        assertFalse(resolved.getSections(0).hasCharStartOffset(), "No body text → no offsets added");
    }

    @Test
    void resolve_titleNotFound_sectionSkipped() {
        String body = "This text has no headings at all.";

        DocOutline outline = DocOutline.newBuilder()
                .addSections(Section.newBuilder().setTitle("Nonexistent Heading").setDepth(0))
                .build();

        DocOutline resolved = SectionOffsetResolver.resolve(outline, body);
        assertFalse(resolved.getSections(0).hasCharStartOffset(), "Title not found → no offset");
    }

    @Test
    void resolve_caseInsensitiveFallback() {
        String body = "INTRODUCTION\nSome text about the intro.";

        DocOutline outline = DocOutline.newBuilder()
                .addSections(Section.newBuilder().setTitle("Introduction").setDepth(0))
                .build();

        DocOutline resolved = SectionOffsetResolver.resolve(outline, body);
        assertTrue(resolved.getSections(0).hasCharStartOffset(), "Case-insensitive match should work");
        assertEquals(0, resolved.getSections(0).getCharStartOffset(), "Found at position 0");
    }

    @Test
    void resolve_maintainsDocumentOrder() {
        String body = "Alpha section content here.\n\nBeta section content here.\n\nGamma section content here.";

        DocOutline outline = DocOutline.newBuilder()
                .addSections(Section.newBuilder().setTitle("Alpha").setDepth(0).setOrderIndex(0))
                .addSections(Section.newBuilder().setTitle("Beta").setDepth(0).setOrderIndex(1))
                .addSections(Section.newBuilder().setTitle("Gamma").setDepth(0).setOrderIndex(2))
                .build();

        DocOutline resolved = SectionOffsetResolver.resolve(outline, body);

        int alphaStart = resolved.getSections(0).getCharStartOffset();
        int betaStart = resolved.getSections(1).getCharStartOffset();
        int gammaStart = resolved.getSections(2).getCharStartOffset();

        assertTrue(alphaStart < betaStart, "Alpha before Beta");
        assertTrue(betaStart < gammaStart, "Beta before Gamma");
    }
}
