#!/bin/bash

# Module Parser Startup Script
# Port: 39001 (Module Parser Service)
# This script helps start the module parser service in development mode
# with proper environment variable detection and instance management.

set -e

# Configuration - Update this path to your dev-assets checkout location
DEV_ASSETS_LOCATION="${DEV_ASSETS_LOCATION:-/home/krickert/IdeaProjects/gitea/dev-assets}"

# Source shared utilities from dev-assets
source "$DEV_ASSETS_LOCATION/scripts/shared-utils.sh"

# Check dependencies
check_dependencies "docker" "java" "git"

# Service configuration
SERVICE_NAME="Module Parser"
SERVICE_PORT="39001"
DESCRIPTION="Document parsing module using Apache Tika for extracting text and metadata"

# Validate we're in the correct directory
validate_project_structure "build.gradle" "src/main/resources/application.properties"

# Set environment variables
export QUARKUS_HTTP_PORT="$SERVICE_PORT"

# Set registration host using Docker bridge detection
set_registration_host "module-parser" "MODULE_PARSER_HOST"

# Sample documents auto-detection and setup
#
# Behavior:
# - If TEST_DOCUMENTS and SAMPLE_DOC_TYPES are already set and valid, use them.
# - Otherwise, choose a base directory for the sample-documents repo in this order:
#   1) SAMPLE_DOCUMENTS_DIR (if set)
#   2) ../sample-documents (one level higher than this project)
#   3) /tmp/sample-documents
# - If the chosen base directory doesn't contain the repo, clone it.
# - Export TEST_DOCUMENTS and SAMPLE_DOC_TYPES based on the checked-out repo.

SAMPLE_DOCS_REPO_URL="https://github.com/ai-pipestream/sample-documents.git"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

validate_dir() {
  local p="$1"
  [[ -n "$p" && -d "$p" ]]
}

resolve_sample_docs_base() {
  # 1) Respect explicit override
  if [[ -n "$SAMPLE_DOCUMENTS_DIR" ]]; then
    echo "$SAMPLE_DOCUMENTS_DIR"
    return
  fi
  # 2) Default to one level higher ../sample-documents when it exists
  local sibling_base="$PROJECT_DIR/../sample-documents"
  if [[ -d "$sibling_base" ]]; then
    echo "$sibling_base"
    return
  fi
  # 3) Fallback to /tmp/sample-documents
  echo "/tmp/sample-documents"
}

ensure_sample_docs_repo() {
  local base_dir="$1"
  if [[ -d "$base_dir/.git" ]]; then
    print_status "info" "Found existing sample-documents repo at: $base_dir"
    # Try a fast-forward pull, but do not fail the script if it doesn't work (offline, etc.)
    git -C "$base_dir" fetch --quiet || true
    git -C "$base_dir" pull --ff-only --quiet || true
  else
    print_status "info" "Cloning sample documents into: $base_dir"
    mkdir -p "$base_dir"
    # Clone directly into the target directory (empty dir recommended)
    if [[ -z "$(ls -A "$base_dir" 2>/dev/null)" ]]; then
      if [[ "$SAMPLE_DOCS_SKIP_CLONE" == "1" || "$SAMPLE_DOCS_SKIP_CLONE" == "true" || "$SAMPLE_DOCS_SKIP_CLONE" == "yes" ]]; then
        print_status "warning" "SAMPLE_DOCS_SKIP_CLONE is set. Skipping clone of sample documents."
      else
        git clone --depth 1 "$SAMPLE_DOCS_REPO_URL" "$base_dir" || {
          print_status "warning" "Clone failed. Please ensure network access or set SAMPLE_DOCUMENTS_DIR/TEST_DOCUMENTS/SAMPLE_DOC_TYPES manually."
        }
      fi
    else
      print_status "warning" "Target directory is not empty: $base_dir. Attempting to clone anyway."
      if [[ "$SAMPLE_DOCS_SKIP_CLONE" == "1" || "$SAMPLE_DOCS_SKIP_CLONE" == "true" || "$SAMPLE_DOCS_SKIP_CLONE" == "yes" ]]; then
        print_status "warning" "SAMPLE_DOCS_SKIP_CLONE is set. Skipping clone of sample documents."
      else
        git clone --depth 1 "$SAMPLE_DOCS_REPO_URL" "$base_dir" || {
          print_status "warning" "Clone failed. Please ensure network access or set SAMPLE_DOCUMENTS_DIR/TEST_DOCUMENTS/SAMPLE_DOC_TYPES manually."
        }
      fi
    fi
  fi
}

configure_sample_docs_env() {
  local base_dir="$1"
  local test_docs_path="$base_dir/test-documents"
  local sample_types_path="$base_dir/sample_doc_types"

  # Only set env vars if not already set or invalid
  if ! validate_dir "$TEST_DOCUMENTS"; then
    if validate_dir "$test_docs_path"; then
      export TEST_DOCUMENTS="$test_docs_path"
    fi
  fi
  if ! validate_dir "$SAMPLE_DOC_TYPES"; then
    if validate_dir "$sample_types_path"; then
      export SAMPLE_DOC_TYPES="$sample_types_path"
    fi
  fi
}

# Try to infer the missing env var from the one that is already set
infer_sample_docs_from_existing() {
  if validate_dir "$TEST_DOCUMENTS" && ! validate_dir "$SAMPLE_DOC_TYPES"; then
    local base_dir
    base_dir="$(cd "$(dirname "$TEST_DOCUMENTS")" && pwd)"
    if validate_dir "$base_dir/sample_doc_types"; then
      export SAMPLE_DOC_TYPES="$base_dir/sample_doc_types"
      print_status "info" "Inferred SAMPLE_DOC_TYPES from TEST_DOCUMENTS: $SAMPLE_DOC_TYPES"
      return 0
    fi
  fi
  if validate_dir "$SAMPLE_DOC_TYPES" && ! validate_dir "$TEST_DOCUMENTS"; then
    local base_dir
    base_dir="$(cd "$(dirname "$SAMPLE_DOC_TYPES")" && pwd)"
    if validate_dir "$base_dir/test-documents"; then
      export TEST_DOCUMENTS="$base_dir/test-documents"
      print_status "info" "Inferred TEST_DOCUMENTS from SAMPLE_DOC_TYPES: $TEST_DOCUMENTS"
      return 0
    fi
  fi
  return 1
}

# If env vars are missing or invalid, try to infer from existing one first
if ! validate_dir "$TEST_DOCUMENTS" || ! validate_dir "$SAMPLE_DOC_TYPES"; then
  infer_sample_docs_from_existing || true
  # If still missing, resolve, optionally clone, and configure
  if ! validate_dir "$TEST_DOCUMENTS" || ! validate_dir "$SAMPLE_DOC_TYPES"; then
    SAMPLE_DOCS_BASE_DIR="$(resolve_sample_docs_base)"
    ensure_sample_docs_repo "$SAMPLE_DOCS_BASE_DIR"
    configure_sample_docs_env "$SAMPLE_DOCS_BASE_DIR"
  fi
fi

print_status "info" "Sample documents configuration:"
echo "  TEST_DOCUMENTS = ${TEST_DOCUMENTS:-not set}"
echo "  SAMPLE_DOC_TYPES = ${SAMPLE_DOC_TYPES:-not set}"
echo

print_status "header" "Starting $SERVICE_NAME"
print_status "info" "Port: $SERVICE_PORT"
print_status "info" "Description: $DESCRIPTION"
print_status "info" "Dev Assets Location: $DEV_ASSETS_LOCATION"
print_status "info" "Configuration:"
echo "  Service Host: $MODULE_PARSER_HOST"
echo "  HTTP Port: $QUARKUS_HTTP_PORT"
echo

# Check if already running and offer to kill
if check_port "$SERVICE_PORT" "$SERVICE_NAME"; then
    print_status "warning" "$SERVICE_NAME is already running on port $SERVICE_PORT."
    read -p "Would you like to kill the existing process and restart? (y/N) " -r response
    if [[ "$response" =~ ^[Yy]$ ]]; then
        kill_process_on_port "$SERVICE_PORT" "$SERVICE_NAME"
    else
        print_status "info" "Cancelled by user."
        exit 0
    fi
fi

print_status "info" "Starting $SERVICE_NAME in Quarkus dev mode..."
print_status "info" "DevServices will automatically start: MySQL, Kafka, Consul, etc."
print_status "info" "Press Ctrl+C to stop"
echo

# Start using the app's own gradlew with the detected registration host
# Pass through sample documents locations if available
GRADLE_PROPS=(
  "-Dmodule.registration.host=$MODULE_PARSER_HOST"
)
if validate_dir "$TEST_DOCUMENTS"; then
  GRADLE_PROPS+=("-Dtest.documents=$TEST_DOCUMENTS")
fi
if validate_dir "$SAMPLE_DOC_TYPES"; then
  GRADLE_PROPS+=("-Dsample.doc.types=$SAMPLE_DOC_TYPES")
fi

./gradlew quarkusDev ${GRADLE_PROPS[*]}