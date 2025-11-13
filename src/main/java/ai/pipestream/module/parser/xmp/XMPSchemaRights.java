package ai.pipestream.module.parser.xmp;

import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.jempbox.xmp.XMPSchema;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * XMP Rights Management Schema for Jempbox.
 * Implements the xmpRights namespace for Creative Commons and rights metadata.
 *
 * @see <a href="http://ns.adobe.com/xap/1.0/rights/">XMP Rights Schema</a>
 */
public class XMPSchemaRights extends XMPSchema {

    public static final String NAMESPACE = "http://ns.adobe.com/xap/1.0/rights/";

    /**
     * Constructor for an existing XMP schema.
     */
    public XMPSchemaRights(XMPMetadata metadata) {
        super(metadata, "xmpRights", NAMESPACE);
    }

    /**
     * Constructor for a new schema within existing metadata.
     */
    public XMPSchemaRights(XMPMetadata metadata, String prefix, String namespace) {
        super(metadata, prefix, namespace);
    }

    /**
     * Constructor from an existing element.
     */
    public XMPSchemaRights(Element element, String prefix) {
        super(element, prefix);
    }

    /**
     * Get the rights certificate URL.
     * @return certificate URL or null
     */
    public String getCertificate() {
        return getTextProperty("xmpRights:Certificate");
    }

    /**
     * Set the rights certificate URL.
     */
    public void setCertificate(String certificate) {
        setTextProperty("xmpRights:Certificate", certificate);
    }

    /**
     * Get the marked status (true if rights-managed, false if public domain).
     * @return marked status or null
     */
    public Boolean getMarked() {
        String value = getTextProperty("xmpRights:Marked");
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return null;
    }

    /**
     * Set the marked status.
     */
    public void setMarked(Boolean marked) {
        if (marked != null) {
            setTextProperty("xmpRights:Marked", marked.toString());
        }
    }

    /**
     * Get the usage terms.
     * @return usage terms or null
     */
    public String getUsageTerms() {
        return getTextProperty("xmpRights:UsageTerms");
    }

    /**
     * Set the usage terms.
     */
    public void setUsageTerms(String usageTerms) {
        setTextProperty("xmpRights:UsageTerms", usageTerms);
    }

    /**
     * Get the web statement URL.
     * @return web statement URL or null
     */
    public String getWebStatement() {
        return getTextProperty("xmpRights:WebStatement");
    }

    /**
     * Set the web statement URL.
     */
    public void setWebStatement(String webStatement) {
        setTextProperty("xmpRights:WebStatement", webStatement);
    }

    /**
     * Get the list of rights owners.
     * @return list of owners or empty list
     */
    public List<String> getOwners() {
        try {
            return getBagList("xmpRights:Owner");
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Add a rights owner.
     */
    public void addOwner(String owner) {
        try {
            addBagValue("xmpRights:Owner", owner);
        } catch (Exception e) {
            // Silently fail
        }
    }
}
