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
        binutils \
        pipx \
        golang-go \
        build-essential \
        autoconf \
        automake \
        libtool \
        texinfo \
        libpcre2-dev \
        swig \
        python3-dev

    echo ""
    echo "Installing additional tools for Ubuntu..."

    # Install Magika (AI-powered file type detection)
    echo "Installing Magika..."
    if ! command -v magika &> /dev/null; then
        # Ensure pipx is available
        if ! command -v pipx &> /dev/null; then
            echo "  Warning: pipx not found, attempting to install..."
            sudo $INSTALL_CMD pipx || echo "  Warning: Could not install pipx"
        fi

        # Install magika using pipx (avoids PEP 668 issues)
        if command -v pipx &> /dev/null; then
            pipx install magika && pipx ensurepath || echo "  Warning: Magika installation failed (optional)"
        else
            echo "  Warning: Skipping Magika - pipx not available"
        fi
    else
        echo "  Magika already installed"
    fi

    # Install Siegfried (file format identification)
    echo "Installing Siegfried..."
    if ! command -v sf &> /dev/null; then
        # Try to install via Go
        if command -v go &> /dev/null; then
            GOPATH=$(go env GOPATH)
            GOBIN="$GOPATH/bin"

            go install github.com/richardlehane/siegfried/cmd/sf@latest || echo "  Warning: Siegfried installation failed (optional)"

            # Add Go bin to PATH for current session
            export PATH="$PATH:$GOBIN"

            # Check if installation succeeded
            if [ -f "$GOBIN/sf" ]; then
                echo "  Siegfried installed to $GOBIN/sf"
                echo "  Note: Add the following to your ~/.bashrc or ~/.zshrc for permanent PATH:"
                echo "    export PATH=\"\$PATH:\$(go env GOPATH)/bin\""
            fi
        else
            echo "  Warning: Go not available, skipping Siegfried"
        fi
    else
        echo "  Siegfried already installed"
    fi

    # Install LibreDWG (AutoCAD DWG file parsing)
    echo "Installing LibreDWG..."
    if ! command -v dwgread &> /dev/null; then
        # Check if git is available
        if ! command -v git &> /dev/null; then
            echo "  Warning: git not found, installing..."
            sudo $INSTALL_CMD git || echo "  Warning: Could not install git"
        fi

        if command -v git &> /dev/null; then
            LIBREDWG_TMP=$(mktemp -d)
            (
                cd "$LIBREDWG_TMP"
                echo "  Cloning LibreDWG repository..."
                git clone --depth 1 https://github.com/LibreDWG/libredwg.git
                cd libredwg
                echo "  Building LibreDWG (this may take a few minutes)..."
                sh autogen.sh
                ./configure
                make -j$(nproc)
                sudo make install
                sudo ldconfig
                echo "  LibreDWG installed successfully"
            ) || echo "  Warning: LibreDWG installation failed (optional)"
            rm -rf "$LIBREDWG_TMP"
        else
            echo "  Warning: Skipping LibreDWG - git not available"
        fi
    else
        echo "  LibreDWG already installed"
    fi

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
    ["magika"]="Magika (AI file detection)"
    ["sf"]="Siegfried (format identification)"
    ["dwgread"]="LibreDWG (AutoCAD)"
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
echo "Additional Tools (Manual install on non-Ubuntu)"
echo "=========================================="
echo ""
if [ "$PKG_MANAGER" = "apt-get" ]; then
    echo "On Ubuntu/Debian, the following were automatically installed above:"
    echo "  - LibreDWG, Siegfried, Magika"
    echo ""
    echo "IMPORTANT: If pipx was just installed, you may need to:"
    echo "  1. Run: pipx ensurepath"
    echo "  2. Restart your shell or run: source ~/.bashrc"
    echo ""
    echo "If any failed, you can manually install:"
else
    echo "The following tools require manual installation:"
fi
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
echo "   Install via pipx (recommended):"
echo "     pipx install magika"
echo "   Or using pip in a virtual environment:"
echo "     python3 -m venv ~/.magika-venv"
echo "     ~/.magika-venv/bin/pip install magika"
echo "     sudo ln -s ~/.magika-venv/bin/magika /usr/local/bin/magika"
echo ""

echo "=========================================="
echo "TIP: To suppress DEBUG-level stack traces for missing tools, set log level to INFO:"
echo "  quarkus.log.category.\"ai.pipestream.shaded.tika.parser.external\".level=INFO"
echo "  quarkus.log.category.\"ai.pipestream.shaded.tika.parser.ocr\".level=INFO"