package ai.pipestream.module.parser.tika.builders;

import com.google.protobuf.Struct;
import ai.pipestream.parsed.data.media.v1.MediaMetadata;
import ai.pipestream.parsed.data.tika.base.v1.TikaBaseFields;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;

import java.util.HashSet;
import java.util.Set;

public class MediaMetadataBuilder {

    public static MediaMetadata build(Metadata md, String parserClass, String tikaVersion) {
        MediaMetadata.Builder b = MediaMetadata.newBuilder();
        Set<String> mapped = new HashSet<>();

        mapCore(md, b, mapped);
        mapXmpdm(md, b, mapped);
        mapParserSpecific(md, b, mapped);
        mapTech(md, b, mapped);
        mapGeoAndParse(md, b, mapped);

        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        b.setAdditionalMetadata(additional);

        TikaBaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        b.setBaseFields(base);

        return b.build();
    }

    private static void mapCore(Metadata md, MediaMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, TikaCoreProperties.TITLE, b::setTitle, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CREATOR, b::setCreator, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.DESCRIPTION, b::setDescription, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.SUBJECT, b::setSubject, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.LANGUAGE, b::setLanguage, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.PUBLISHER, b::setPublisher, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.RIGHTS, b::setRights, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.FORMAT, b::setFormat, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.IDENTIFIER, b::setIdentifier, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CONTRIBUTOR, b::setContributor, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.COVERAGE, b::setCoverage, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.RELATION, b::setRelation, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.SOURCE, b::setSource, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.TYPE, b::setType, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.CREATED, b::setCreated, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.MODIFIED, b::setModified, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CREATOR_TOOL, b::setCreatorTool, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.COMMENTS, b::setComments, mapped);
        // Rating is often string; try parse int
        MetadataUtils.mapIntField(md, TikaCoreProperties.RATING, b::setRating, mapped);
    }

    private static void mapXmpdm(Metadata md, MediaMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, XMPDM.ABS_PEAK_AUDIO_FILE_PATH, b::setAbsPeakAudioFilePath, mapped);
        MetadataUtils.mapStringField(md, XMPDM.ALBUM, b::setAlbum, mapped);
        MetadataUtils.mapStringField(md, XMPDM.ALT_TAPE_NAME, b::setAltTapeName, mapped);
        MetadataUtils.mapStringField(md, XMPDM.ARTIST, b::setArtist, mapped);
        MetadataUtils.mapStringField(md, XMPDM.ALBUM_ARTIST, b::setAlbumArtist, mapped);
        MetadataUtils.mapTimestampField(md, XMPDM.AUDIO_MOD_DATE, b::setAudioModDate, mapped);
        MetadataUtils.mapIntField(md, XMPDM.AUDIO_SAMPLE_RATE, b::setAudioSampleRate, mapped);
        MetadataUtils.mapStringField(md, XMPDM.AUDIO_SAMPLE_TYPE, b::setAudioSampleType, mapped);
        MetadataUtils.mapStringField(md, XMPDM.AUDIO_CHANNEL_TYPE, b::setAudioChannelType, mapped);
        MetadataUtils.mapStringField(md, XMPDM.AUDIO_COMPRESSOR, b::setAudioCompressor, mapped);
        MetadataUtils.mapIntField(md, XMPDM.COMPILATION, b::setCompilation, mapped);
        MetadataUtils.mapStringField(md, XMPDM.COMPOSER, b::setComposer, mapped);
        MetadataUtils.mapStringField(md, XMPDM.COPYRIGHT, b::setCopyright, mapped);
        MetadataUtils.mapIntField(md, XMPDM.DISC_NUMBER, b::setDiscNumber, mapped);
        MetadataUtils.mapDoubleField(md, XMPDM.DURATION, b::setDuration, mapped);
        MetadataUtils.mapStringField(md, XMPDM.ENGINEER, b::setEngineer, mapped);
        // FILE_DATA_RATE is rational string; map to string field in proto
        MetadataUtils.mapStringField(md, XMPDM.FILE_DATA_RATE, b::setFileDataRate, mapped);
        MetadataUtils.mapStringField(md, XMPDM.GENRE, b::setGenre, mapped);
        MetadataUtils.mapStringField(md, XMPDM.INSTRUMENT, b::setInstrument, mapped);
        MetadataUtils.mapStringField(md, XMPDM.KEY, b::setKey, mapped);
        MetadataUtils.mapStringField(md, XMPDM.LOG_COMMENT, b::setLogComment, mapped);
        MetadataUtils.mapBooleanField(md, XMPDM.LOOP, b::setLoop, mapped);
        MetadataUtils.mapDoubleField(md, XMPDM.NUMBER_OF_BEATS, b::setNumberOfBeats, mapped);
        MetadataUtils.mapTimestampField(md, XMPDM.METADATA_MOD_DATE, b::setMetadataModDate, mapped);
        MetadataUtils.mapStringField(md, XMPDM.PULL_DOWN, b::setPullDown, mapped);
        MetadataUtils.mapStringField(md, XMPDM.RELATIVE_PEAK_AUDIO_FILE_PATH, b::setRelativePeakAudioFilePath, mapped);
        MetadataUtils.mapTimestampField(md, XMPDM.RELEASE_DATE, b::setReleaseDate, mapped);
        MetadataUtils.mapStringField(md, XMPDM.SCALE_TYPE, b::setScaleType, mapped);
        MetadataUtils.mapStringField(md, XMPDM.SCENE, b::setScene, mapped);
        MetadataUtils.mapTimestampField(md, XMPDM.SHOT_DATE, b::setShotDate, mapped);
        MetadataUtils.mapStringField(md, XMPDM.SHOT_LOCATION, b::setShotLocation, mapped);
        MetadataUtils.mapStringField(md, XMPDM.SHOT_NAME, b::setShotName, mapped);
        MetadataUtils.mapStringField(md, XMPDM.SPEAKER_PLACEMENT, b::setSpeakerPlacement, mapped);
        MetadataUtils.mapStringField(md, XMPDM.STRETCH_MODE, b::setStretchMode, mapped);
        MetadataUtils.mapStringField(md, XMPDM.TAPE_NAME, b::setTapeName, mapped);
        MetadataUtils.mapDoubleField(md, XMPDM.TEMPO, b::setTempo, mapped);
        MetadataUtils.mapStringField(md, XMPDM.TIME_SIGNATURE, b::setTimeSignature, mapped);
        MetadataUtils.mapIntField(md, XMPDM.TRACK_NUMBER, b::setTrackNumber, mapped);
        MetadataUtils.mapStringField(md, XMPDM.VIDEO_ALPHA_MODE, b::setVideoAlphaMode, mapped);
        MetadataUtils.mapBooleanField(md, XMPDM.VIDEO_ALPHA_UNITY_IS_TRANSPARENT, b::setVideoAlphaUnityIsTransparent, mapped);
        MetadataUtils.mapStringField(md, XMPDM.VIDEO_COLOR_SPACE, b::setVideoColorSpace, mapped);
        MetadataUtils.mapStringField(md, XMPDM.VIDEO_COMPRESSOR, b::setVideoCompressor, mapped);
        MetadataUtils.mapStringField(md, XMPDM.VIDEO_FIELD_ORDER, b::setVideoFieldOrder, mapped);
        MetadataUtils.mapStringField(md, XMPDM.VIDEO_FRAME_RATE, b::setVideoFrameRate, mapped);
        MetadataUtils.mapTimestampField(md, XMPDM.VIDEO_MOD_DATE, b::setVideoModDate, mapped);
        MetadataUtils.mapStringField(md, XMPDM.VIDEO_PIXEL_DEPTH, b::setVideoPixelDepth, mapped);
        MetadataUtils.mapStringField(md, XMPDM.VIDEO_PIXEL_ASPECT_RATIO, b::setVideoPixelAspectRatio, mapped);
    }

    private static void mapParserSpecific(Metadata md, MediaMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, "Content-Type", b::setContentType, mapped);
        // Common parser outputs for MP3/MP4
        MetadataUtils.mapStringField(md, "samplerate", b::setSamplerate, mapped);
        MetadataUtils.mapStringField(md, "channels", b::setChannels, mapped);
        MetadataUtils.mapStringField(md, "version", b::setVersion, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.METADATA_DATE, v -> {}, mapped); // no direct field
        MetadataUtils.mapStringField(md, TikaCoreProperties.PRINT_DATE, v -> {}, mapped); // ignore for media
        // Year often appears in ID3 tags
        MetadataUtils.mapStringField(md, "year", b::setYear, mapped);
    }

    private static void mapTech(Metadata md, MediaMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapLongField(md, TikaCoreProperties.PARSE_TIME_MILLIS, v -> {}, mapped); // just to suppress unknown
        // Technical
        MetadataUtils.mapIntField(md, "bitrate", b::setBitrate, mapped);
        MetadataUtils.mapIntField(md, "audio:bitrate", b::setAudioBitrate, mapped);
        MetadataUtils.mapIntField(md, "audio:channels", b::setAudioChannels, mapped);
        MetadataUtils.mapIntField(md, "audio:bitsPerSample", b::setAudioBitsPerSample, mapped);
        MetadataUtils.mapIntField(md, "xmpDM:videoFrameRate", v -> {
            try { b.setVideoFrameRateNumeric((double) v); } catch (Exception ignore) {}
        }, mapped);
        MetadataUtils.mapIntField(md, "tiff:ImageWidth", b::setVideoWidth, mapped);
        MetadataUtils.mapIntField(md, "tiff:ImageLength", b::setVideoHeight, mapped);
        MetadataUtils.mapStringField(md, "xmpDM:pixelAspectRatio", b::setAspectRatio, mapped);
        MetadataUtils.mapStringField(md, "xmpDM:majorBrand", b::setMajorBrand, mapped);
        MetadataUtils.mapStringField(md, "xmpDM:codec", b::setCodec, mapped);
    }

    private static void mapGeoAndParse(Metadata md, MediaMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapDoubleField(md, TikaCoreProperties.LATITUDE, b::setLatitude, mapped);
        MetadataUtils.mapDoubleField(md, TikaCoreProperties.LONGITUDE, b::setLongitude, mapped);
        MetadataUtils.mapDoubleField(md, TikaCoreProperties.ALTITUDE, b::setAltitude, mapped);

        MetadataUtils.mapRepeatedStringField(md, TikaCoreProperties.TIKA_PARSED_BY, b::addAllParsedBy, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.TIKA_DETECTED_LANGUAGE, b::setDetectedLanguage, mapped);
        MetadataUtils.mapDoubleField(md, TikaCoreProperties.TIKA_DETECTED_LANGUAGE_CONFIDENCE_RAW, b::setDetectedLanguageConfidence, mapped);
        MetadataUtils.mapBooleanField(md, TikaCoreProperties.HAS_SIGNATURE, b::setHasSignature, mapped);
        MetadataUtils.mapBooleanField(md, TikaCoreProperties.IS_ENCRYPTED, b::setIsEncrypted, mapped);
    }
}
