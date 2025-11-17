package ai.pipestream.module.parser.xmp;

import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.jempbox.xmp.XMPSchema;
import ai.pipestream.shaded.tika.metadata.Metadata;
import ai.pipestream.shaded.tika.metadata.Property;
import ai.pipestream.shaded.tika.metadata.XMPRights;
import ai.pipestream.shaded.tika.parser.xmp.JempboxExtractor;
import org.jboss.logging.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Enhanced Jempbox extractor that adds XMP Rights Management schema extraction.
 * Extends the standard JempboxExtractor to extract Creative Commons and Rights metadata.
 */
public class EnhancedJempboxExtractor extends JempboxExtractor {
    private static final Logger LOG = Logger.getLogger(EnhancedJempboxExtractor.class);

    private static final String XMP_RIGHTS_NS = "http://ns.adobe.com/xap/1.0/rights/";
    private static final String XMP_RIGHTS_PREFIX = "xmpRights";
    private static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    public EnhancedJempboxExtractor(Metadata metadata) {
        super(metadata);
    }

    private org.apache.jempbox.xmp.XMPMetadata xmpMetadata;

    /**
     * Parse XMP and extract all schemas including XMP Rights.
     * This overrides the parent to add Rights extraction.
     */
    @Override
    public void parse(InputStream is) throws IOException, ai.pipestream.shaded.tika.exception.TikaException {
        // The parent JempboxExtractor will scan for XMP packets and parse them
        super.parse(is);

        // Now we need to re-scan and re-parse to get the XMPMetadata object for Rights extraction
        // The XMPPacketScanner is used internally by JempboxExtractor
        try (ai.pipestream.shaded.tika.io.TikaInputStream tis = ai.pipestream.shaded.tika.io.TikaInputStream.cast(is)) {
            if (tis != null && tis.hasFile()) {
                // Re-scan for XMP packet
                try (java.io.FileInputStream fis = new java.io.FileInputStream(tis.getFile())) {
                    org.apache.jempbox.xmp.XMPMetadata xmp = org.apache.jempbox.xmp.XMPMetadata.load(fis);
                    if (xmp != null) {
                        // Extract Rights that the parent missed
                        extractXMPRights(xmp, (Metadata) this.getClass().getField("metadata").get(this));
                    }
                } catch (Exception e) {
                    LOG.debugf("Could not re-parse for Rights: %s", e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.tracef("No TikaInputStream available for re-parsing: %s", e.getMessage());
        }
    }

    /**
     * Extracts XMP Rights Management schema from XMP metadata.
     * This includes Creative Commons license information and usage rights.
     *
     * @param xmpMetadata XMPMetadata to process
     * @param metadata Tika's metadata to write to
     */
    public static void extractXMPRights(XMPMetadata xmpMetadata, Metadata metadata) {
        if (xmpMetadata == null) {
            return;
        }

        try {
            // Try to get the Rights schema using our custom class
            XMPSchema rightsSchema = xmpMetadata.getSchemaByClass(XMPSchemaRights.class);
            if (rightsSchema instanceof XMPSchemaRights) {
                LOG.debugf("Found xmpRights schema via schema class, extracting...");
                extractFromRightsSchema((XMPSchemaRights) rightsSchema, metadata);
                return;
            }
        } catch (IOException e) {
            LOG.debugf("Could not get Rights schema via Jempbox schema class: %s", e.getMessage());
        }

        // Fallback: manually parse the XMP DOM for xmpRights properties
        try {
            // Get the XML document from XMPMetadata
            org.w3c.dom.Document xmpDoc = xmpMetadata.getXMPDocument();
            if (xmpDoc != null) {
                Element rootElement = xmpDoc.getDocumentElement();
                extractRightsFromDOM(rootElement, metadata);
            }
        } catch (Exception e) {
            LOG.debugf("Could not extract Rights from DOM: %s", e.getMessage());
        }
    }

    /**
     * Extract rights from a proper XMPSchemaRights object
     */
    private static void extractFromRightsSchema(XMPSchemaRights schema, Metadata metadata) {
        addIfNotNull(metadata, XMPRights.CERTIFICATE, schema.getCertificate());

        Boolean marked = schema.getMarked();
        if (marked != null) {
            metadata.set(XMPRights.MARKED, marked.toString());
        }

        addIfNotNull(metadata, XMPRights.USAGE_TERMS, schema.getUsageTerms());
        addIfNotNull(metadata, XMPRights.WEB_STATEMENT, schema.getWebStatement());

        List<String> owners = schema.getOwners();
        if (owners != null && !owners.isEmpty()) {
            for (String owner : owners) {
                metadata.add(XMPRights.OWNER.getName(), owner);
            }
        }
    }

    /**
     * Manually extract xmpRights properties from the DOM when schema object isn't available
     */
    private static void extractRightsFromDOM(Element element, Metadata metadata) {
        // Look for xmpRights:Certificate
        String certificate = getAttributeOrChildNS(element, XMP_RIGHTS_NS, "Certificate");
        addIfNotNull(metadata, XMPRights.CERTIFICATE, certificate);

        // Look for xmpRights:Marked
        String marked = getAttributeOrChildNS(element, XMP_RIGHTS_NS, "Marked");
        addIfNotNull(metadata, XMPRights.MARKED, marked);

        // Look for xmpRights:UsageTerms
        String usageTerms = getAttributeOrChildNS(element, XMP_RIGHTS_NS, "UsageTerms");
        addIfNotNull(metadata, XMPRights.USAGE_TERMS, usageTerms);

        // Look for xmpRights:WebStatement
        String webStatement = getAttributeOrChildNS(element, XMP_RIGHTS_NS, "WebStatement");
        addIfNotNull(metadata, XMPRights.WEB_STATEMENT, webStatement);

        // Look for xmpRights:Owner (can be a bag/array)
        extractBagProperty(element, XMP_RIGHTS_NS, "Owner", metadata, XMPRights.OWNER);
    }

    /**
     * Get an attribute or child element value from a specific namespace
     */
    private static String getAttributeOrChildNS(Element element, String namespaceURI, String localName) {
        // First try as attribute
        String value = element.getAttributeNS(namespaceURI, localName);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        // Then try as child element
        NodeList children = element.getElementsByTagNameNS(namespaceURI, localName);
        if (children.getLength() > 0) {
            Element child = (Element) children.item(0);
            return child.getTextContent();
        }

        // Also check rdf:Description children
        NodeList descriptions = element.getElementsByTagNameNS(RDF_NS, "Description");
        for (int i = 0; i < descriptions.getLength(); i++) {
            Element desc = (Element) descriptions.item(i);
            value = desc.getAttributeNS(namespaceURI, localName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        return null;
    }

    /**
     * Extract a bag property (array of values)
     */
    private static void extractBagProperty(Element element, String namespaceURI,
                                          String localName, Metadata metadata, Property property) {
        NodeList children = element.getElementsByTagNameNS(namespaceURI, localName);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element bagElement = (Element) node;
                // Look for rdf:Bag/rdf:li elements
                NodeList bags = bagElement.getElementsByTagNameNS(RDF_NS, "Bag");
                for (int j = 0; j < bags.getLength(); j++) {
                    Element bag = (Element) bags.item(j);
                    NodeList liElements = bag.getElementsByTagNameNS(RDF_NS, "li");
                    for (int k = 0; k < liElements.getLength(); k++) {
                        String value = liElements.item(k).getTextContent();
                        if (value != null && !value.trim().isEmpty()) {
                            metadata.add(property.getName(), value.trim());
                        }
                    }
                }
            }
        }
    }

    /**
     * Helper to add metadata only if value is not null
     */
    private static void addIfNotNull(Metadata metadata, Property property, String value) {
        if (value != null && !value.trim().isEmpty()) {
            metadata.set(property, value.trim());
        }
    }
}
