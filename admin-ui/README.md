# Parser Admin UI

Standalone admin/testing front end for the parser module. Renders **ParserConfig** (Tika + Docling options) 1:1 via JSONForms + Vuetify, lets you upload a file or pick a sample document, and POST to the parser's `parse-file` endpoint.

## Stack

- Vue 3, Vite, Vuetify 3
- `@jsonforms/vue`, `@jsonforms/vue-vuetify` (no custom renderers; form is fully driven by the schema from the backend)

## Prerequisites

- Node.js 18+ and npm (or use Quinoa's package-manager-install if configured in the main app)

## Development

### Run with parser (Quinoa dev mode)

From the **module-parser** root (parent of `admin-ui/`):

```bash
./gradlew quarkusDev
```

Quinoa will install Node/NPM (if needed), run `npm install`, start Vite on port 5173, and proxy `/modules/parser/admin/` to the dev server. Quinoa also injects `VITE_ROOT_PATH` and `VITE_API_PATH` so the UI always calls the correct backend base path. Open:

- **Admin UI:** http://localhost:39001/modules/parser/admin/
- **Legacy index:** http://localhost:39001/modules/parser/

API calls from the admin UI are relative to the parser root path (`/modules/parser`), so schema and parse-file work without CORS.

### Run UI only (no backend)

From **admin-ui/**:

```bash
npm install
npm run dev
```

Then open http://localhost:5173/admin/ (or the port Vite prints). Schema and parse will fail unless you point the app at a running parser (e.g. by proxying in Vite or running the parser on the same host and adjusting the base URL).

## Build and package

From **module-parser** root:

```bash
./gradlew build
```

Quinoa builds the admin UI (`npm run build` in `admin-ui/`), copies the output into the Quarkus build, and serves it at `/modules/parser/admin/` when you run the JAR.

To build only the UI (e.g. to verify it compiles):

```bash
cd admin-ui
npm install
npm run build
```

Output is in `admin-ui/dist/`.

## Sample documents

The parser serves sample PDFs from `src/main/resources/META-INF/resources/samples/` (e.g. `MagPi145.pdf`, `irs_f1040.pdf`). The admin UI "Use sample document" dropdown fetches these from `/samples/<filename>` (relative to the parser root path) and uses the blob as the file in the parse request.

## Config 1:1

The form is driven entirely by the **resolved** ParserConfig schema from `GET api/parser/service/config/jsonforms`. No hand-maintained list of options; Tika and Docling options come from the Java records and OpenAPI schema. Form data is sent as-is in the `config` field of the parse-file request.
