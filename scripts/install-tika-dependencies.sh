#!/bin/bash
# Install external tools required for Apache Tika full functionality
# These are optional - Tika will work without them but with reduced capabilities

set -e

echo "Installing Tika external dependencies..."

# Detect package manager
if command -v apt-get &> /dev/null; then
    PKG_MANAGER="apt-get"
    INSTALL_CMD="apt-get install -y"
    UPDATE_CMD="apt-get update"
elif command -v dnf &> /dev/null; then
    PKG_MANAGER="dnf"
    INSTALL_CMD="dnf install -y"
    UPDATE_CMD="dnf check-update || true"
elif command -v yum &> /dev/null; then
    PKG_MANAGER="yum"
    INSTALL_CMD="yum install -y"
    UPDATE_CMD="yum check-update || true"
elif command -v apk &> /dev/null; then
    PKG_MANAGER="apk"
    INSTALL_CMD="apk add --no-cache"
    UPDATE_CMD="apk update"
else
    echo "Unsupported package manager. Please install dependencies manually."
    exit 1
fi

echo "Detected package manager: $PKG_MANAGER"

# Update package index
echo "Updating package index..."
sudo $UPDATE_CMD

# Install packages based on distribution
if [ "$PKG_MANAGER" = "apt-get" ]; then
    echo "Installing packages for Debian/Ubuntu..."

    sudo $INSTALL_CMD \
        tesseract-ocr \
        tesseract-ocr-all \
        imagemagick \
        ffmpeg \
        libimage-exiftool-perl \
        sox \
        unrar-free \
        mupdf-tools \
        ghostscript \
        poppler-utils \
        gdal-bin \
        pst-utils \
        file \
        binutils

elif [ "$PKG_MANAGER" = "dnf" ] || [ "$PKG_MANAGER" = "yum" ]; then
    echo "Installing packages for RHEL/Fedora/CentOS..."

    sudo $INSTALL_CMD \
        tesseract \
        tesseract-langpack-* \
        ImageMagick \
        ffmpeg \
        perl-Image-ExifTool \
        sox \
        unrar \
        mupdf-tools \
        ghostscript \
        poppler-utils \
        gdal \
        libpst \
        file \
        binutils

elif [ "$PKG_MANAGER" = "apk" ]; then
    echo "Installing packages for Alpine..."

    sudo $INSTALL_CMD \
        tesseract-ocr \
        imagemagick \
        ffmpeg \
        exiftool \
        sox \
        mupdf-tools \
        ghostscript \
        poppler-utils \
        gdal \
        file \
        binutils
fi

echo ""
echo "Verifying installations..."

# Check each tool
declare -A TOOLS=(
    ["tesseract"]="Tesseract OCR"
    ["convert"]="ImageMagick"
    ["ffmpeg"]="FFmpeg"
    ["exiftool"]="ExifTool"
    ["sox"]="SoX Audio"
    ["unrar"]="UnRAR"
    ["mutool"]="MuPDF Tools"
    ["gs"]="Ghostscript"
    ["pdftotext"]="Poppler Utils"
    ["gdalinfo"]="GDAL"
    ["readpst"]="PST Utils"
    ["file"]="File Command"
    ["strings"]="GNU Strings"
)

MISSING=0
for tool in "${!TOOLS[@]}"; do
    if command -v "$tool" &> /dev/null; then
        echo "  ✓ ${TOOLS[$tool]} ($tool) - installed"
    else
        echo "  ✗ ${TOOLS[$tool]} ($tool) - NOT FOUND"
        MISSING=$((MISSING + 1))
    fi
done

echo ""
if [ $MISSING -eq 0 ]; then
    echo "All Tika external dependencies installed successfully!"
else
    echo "WARNING: $MISSING tool(s) not found. Some Tika features may be limited."
    echo "This is normal - Tika will work without these tools but won't process certain file types."
fi

echo ""
echo "=========================================="
echo "Additional Tools (Not in Standard Repos)"
echo "=========================================="
echo ""
echo "The following tools require manual installation:"
echo ""

echo "1. LibreDWG (AutoCAD DWG file parsing)"
echo "   Project: https://github.com/LibreDWG/libredwg"
echo "   Install from source:"
echo "     git clone https://github.com/LibreDWG/libredwg.git"
echo "     cd libredwg"
echo "     sh autogen.sh"
echo "     ./configure"
echo "     make && sudo make install"
echo ""

echo "2. Siegfried (File format identification)"
echo "   Project: https://github.com/richardlehane/siegfried"
echo "   Install via Go:"
echo "     go install github.com/richardlehane/siegfried/cmd/sf@latest"
echo "   Or download binary from:"
echo "     https://github.com/richardlehane/siegfried/releases"
echo ""

echo "3. Magika (AI-powered file type detection)"
echo "   Project: https://github.com/google/magika"
echo "   Install via pip:"
echo "     pip install magika"
echo "   Or:"
echo "     pip3 install magika"
echo ""

echo "=========================================="
echo "TIP: To suppress DEBUG-level stack traces for missing tools, set log level to INFO:"
echo "  quarkus.log.category.\"ai.pipestream.shaded.tika.parser.external\".level=INFO"
echo "  quarkus.log.category.\"ai.pipestream.shaded.tika.parser.ocr\".level=INFO"