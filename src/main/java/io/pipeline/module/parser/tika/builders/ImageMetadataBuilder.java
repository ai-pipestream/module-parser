package io.pipeline.module.parser.tika.builders;

import com.google.protobuf.Struct;
import io.pipeline.parsed.data.image.v1.ImageMetadata;
import io.pipeline.parsed.data.image.v1.ExifMetadata;
import io.pipeline.parsed.data.image.v1.GpsMetadata;
import io.pipeline.parsed.data.image.v1.IptcMetadata;
import io.pipeline.parsed.data.image.v1.ContactInfo;
import io.pipeline.parsed.data.image.v1.LocationInfo;
import io.pipeline.parsed.data.image.v1.ArtworkInfo;
import io.pipeline.parsed.data.image.v1.RegistryInfo;
import io.pipeline.parsed.data.image.v1.PlusLicensingInfo;
import io.pipeline.parsed.data.image.v1.LicensorInfo;
import io.pipeline.parsed.data.tika.base.v1.TikaBaseFields;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Photoshop;
import org.apache.tika.metadata.TikaCoreProperties;

import java.util.HashSet;
import java.util.Set;

public class ImageMetadataBuilder {

    public static ImageMetadata build(Metadata md, String parserClass, String tikaVersion) {
        ImageMetadata.Builder b = ImageMetadata.newBuilder();
        Set<String> mapped = new HashSet<>();

        mapBasic(md, b, mapped);
        mapDates(md, b, mapped);
        mapExif(md, b, mapped);
        mapGps(md, b, mapped);
        mapIptc(md, b, mapped);
        mapAdditional(md, b, mapped);

        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        b.setAdditionalMetadata(additional);

        TikaBaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        b.setBaseFields(base);

        return b.build();
    }

    private static void mapBasic(Metadata md, ImageMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapIntField(md, TIFF.IMAGE_WIDTH, b::setWidth, mapped);
        MetadataUtils.mapIntField(md, TIFF.IMAGE_LENGTH, b::setHeight, mapped);
        MetadataUtils.mapRepeatedIntField(md, TIFF.BITS_PER_SAMPLE, b::addAllBitsPerSample, mapped);
        MetadataUtils.mapIntField(md, TIFF.SAMPLES_PER_PIXEL, b::setSamplesPerPixel, mapped);
        // Orientation in Tika is closed-choice strings 1-8
        MetadataUtils.mapIntField(md, TIFF.ORIENTATION, b::setOrientation, mapped);
        MetadataUtils.mapDoubleField(md, TIFF.RESOLUTION_HORIZONTAL, b::setXResolution, mapped);
        MetadataUtils.mapDoubleField(md, TIFF.RESOLUTION_VERTICAL, b::setYResolution, mapped);
        MetadataUtils.mapStringField(md, TIFF.RESOLUTION_UNIT, b::setResolutionUnit, mapped);
        MetadataUtils.mapStringField(md, TIFF.EQUIPMENT_MAKE, b::setMake, mapped);
        MetadataUtils.mapStringField(md, TIFF.EQUIPMENT_MODEL, b::setModel, mapped);
        MetadataUtils.mapStringField(md, TIFF.SOFTWARE, b::setSoftware, mapped);
        // DC overlaps kept in image proto for convenience
        MetadataUtils.mapStringField(md, Photoshop.CITY, v -> b.getIptcBuilder().setCity(v), mapped);
        MetadataUtils.mapStringField(md, Photoshop.COUNTRY, v -> b.getIptcBuilder().setCountryName(v), mapped);
        MetadataUtils.mapStringField(md, Photoshop.STATE, v -> b.getIptcBuilder().setProvinceState(v), mapped);
    }

    private static void mapDates(Metadata md, ImageMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapTimestampField(md, TIFF.ORIGINAL_DATE, b::setDateTimeOriginal, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.MODIFIED, b::setDateTime, mapped);
    }

    private static void mapExif(Metadata md, ImageMetadata.Builder b, Set<String> mapped) {
        ExifMetadata.Builder ex = ExifMetadata.newBuilder();
        MetadataUtils.mapDoubleField(md, org.apache.tika.metadata.Metadata.EXPOSURE_TIME, ex::setExposureTime, mapped);
        MetadataUtils.mapDoubleField(md, org.apache.tika.metadata.Metadata.F_NUMBER, ex::setFNumber, mapped);
        MetadataUtils.mapDoubleField(md, org.apache.tika.metadata.Metadata.FOCAL_LENGTH, ex::setFocalLength, mapped);
        MetadataUtils.mapRepeatedIntField(md, TIFF.ISO_SPEED_RATINGS, ex::addAllIsoSpeedRatings, mapped);
        MetadataUtils.mapStringField(md, org.apache.tika.metadata.Metadata.FLASH_FIRED, v -> ex.setFlashFired(Boolean.parseBoolean(v)), mapped);
        // Many EXIF fields are strings (WhiteBalance, etc.)
        // Here we set only the key ones; additional go to additional_metadata
        b.setExif(ex.build());
    }

    private static void mapGps(Metadata md, ImageMetadata.Builder b, Set<String> mapped) {
        GpsMetadata.Builder g = GpsMetadata.newBuilder();
        MetadataUtils.mapDoubleField(md, TikaCoreProperties.LATITUDE, g::setLatitude, mapped);
        MetadataUtils.mapDoubleField(md, TikaCoreProperties.LONGITUDE, g::setLongitude, mapped);
        // Timestamp may be stored in Geographic.TIMESTAMP
        MetadataUtils.mapTimestampField(md, org.apache.tika.metadata.Geographic.TIMESTAMP, g::setTimestamp, mapped);
        b.setGps(g.build());
    }

    private static void mapIptc(Metadata md, ImageMetadata.Builder b, Set<String> mapped) {
        IptcMetadata.Builder i = IptcMetadata.newBuilder();
        MetadataUtils.mapStringField(md, IPTC.HEADLINE, i::setHeadline, mapped);
        MetadataUtils.mapStringField(md, IPTC.DESCRIPTION, i::setCaption, mapped);
        MetadataUtils.mapRepeatedStringField(md, IPTC.KEYWORDS, i::addAllKeywords, mapped);
        MetadataUtils.mapStringField(md, IPTC.CATEGORY, i::setCategory, mapped);
        MetadataUtils.mapRepeatedStringField(md, IPTC.SUPPLEMENTAL_CATEGORIES, i::addAllSupplementalCategories, mapped);
        MetadataUtils.mapStringField(md, IPTC.INTELLECTUAL_GENRE, i::setIntellectualGenre, mapped);
        MetadataUtils.mapRepeatedStringField(md, IPTC.SCENE_CODE, i::addAllSceneCode, mapped);
        MetadataUtils.mapRepeatedStringField(md, IPTC.SUBJECT_CODE, i::addAllSubjectCode, mapped);
        MetadataUtils.mapStringField(md, IPTC.TITLE, i::setTitle, mapped);
        MetadataUtils.mapStringField(md, IPTC.PERSON, v -> i.addPersonInImage(v), mapped);
        MetadataUtils.mapStringField(md, IPTC.CREDIT_LINE, i::setCredit, mapped);
        MetadataUtils.mapStringField(md, IPTC.SOURCE, i::setSource, mapped);
        MetadataUtils.mapStringField(md, IPTC.COPYRIGHT_NOTICE, i::setCopyrightNotice, mapped);
        MetadataUtils.mapStringField(md, IPTC.DESCRIPTION_WRITER, i::setDescriptionWriter, mapped);
        // Contact info and extensions omitted for brevity; captured in additional_metadata
        b.setIptc(i.build());
    }

    private static void mapAdditional(Metadata md, ImageMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapIntField(md, TIFF.EXIF_PAGE_COUNT, b::setPageCount, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.COMMENTS, v -> b.addKeywords(v), mapped);
    }
}
