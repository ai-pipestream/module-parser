<template>
  <v-app>
    <v-main>
      <v-container class="py-6">
        <v-card class="mx-auto" max-width="1200">
          <v-card-title class="text-h5 pa-4 bg-primary text-white">
            Parser Admin – Pipeline Test
          </v-card-title>
          <v-card-subtitle class="pa-4">
            Configure ParserConfig (Tika + Docling) 1:1, upload a file or use a sample, then parse.
          </v-card-subtitle>

          <v-card-text class="pa-4">
            <!-- Schema loading error -->
            <v-alert v-if="schemaError" type="error" class="mb-4" closable>
              {{ schemaError }}
              <template #append>
                <v-btn variant="text" size="small" @click="loadSchema">Retry</v-btn>
              </template>
            </v-alert>

            <!-- Loading schema -->
            <div v-else-if="!schema && !schemaError" class="text-center py-8">
              <v-progress-circular indeterminate color="primary" size="48" />
              <p class="mt-2 text-medium-emphasis">Loading ParserConfig schema…</p>
            </div>

            <template v-else>
              <!-- Config form (1:1 from schema) -->
              <v-expansion-panels v-model="formOpen" class="mb-4">
                <v-expansion-panel>
                  <v-expansion-panel-title>Parser configuration</v-expansion-panel-title>
                  <v-expansion-panel-text>
                    <div class="jsonforms-container">
                      <JsonForms
                        v-if="schema"
                        :data="config"
                        :schema="schema"
                        :uischema="uischema"
                        :renderers="renderers"
                        @change="onConfigChange"
                      />
                    </div>
                  </v-expansion-panel-text>
                </v-expansion-panel>
              </v-expansion-panels>

              <!-- File input and sample picker -->
              <v-row class="mb-4">
                <v-col cols="12" md="6">
                  <v-file-input
                    v-model="selectedFiles"
                    label="Upload document"
                    prepend-icon=""
                    prepend-inner-icon="mdi-paperclip"
                    accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.html,.htm,.txt,.md,image/*"
                    clearable
                    show-size
                    variant="outlined"
                    density="comfortable"
                    @update:model-value="onFileSelected"
                  />
                </v-col>
                <v-col cols="12" md="6">
                  <v-select
                    v-model="selectedSample"
                    :items="sampleOptions"
                    label="Or use sample document"
                    clearable
                    variant="outlined"
                    density="comfortable"
                    @update:model-value="onSampleSelected"
                  />
                </v-col>
              </v-row>

              <!-- Submit -->
              <div class="d-flex align-center gap-2 mb-4">
                <v-btn
                  color="primary"
                  :disabled="!hasFile || parsing"
                  :loading="parsing"
                  @click="parse"
                >
                  {{ parsing ? 'Parsing…' : 'Parse document' }}
                </v-btn>
                <v-btn variant="outlined" :disabled="!parseResult" @click="clearResults">
                  Clear results
                </v-btn>
              </div>

              <!-- Parse result -->
              <div v-if="parseResult" class="result-area">
                <v-alert
                  v-if="parseResult.success !== false && !parseResult.error"
                  type="success"
                  class="mb-4"
                >
                  Document parsed successfully. Tika and Docling ran as configured.
                </v-alert>
                <v-alert v-else-if="parseResult.error" type="error" class="mb-4">
                  {{ parseResult.error }}
                </v-alert>

                <template v-if="parseResult.output_doc">
                  <v-row>
                    <!-- Tika -->
                    <v-col cols="12" md="6">
                      <v-card variant="outlined" class="parser-box">
                        <v-card-title class="d-flex align-center gap-2">
                          <v-chip color="primary" size="small">Tika</v-chip>
                        </v-card-title>
                        <v-card-text>
                          <div v-if="tikaMeta" class="metadata-summary mb-2">
                            <div class="text-caption text-medium-emphasis">Metadata</div>
                            <div v-if="tikaMeta.parser_name">
                              Parser: {{ tikaMeta.parser_name }}
                              <span v-if="tikaMeta.parser_version"> v{{ tikaMeta.parser_version }}</span>
                            </div>
                          </div>
                          <v-btn
                            variant="text"
                            size="small"
                            class="text-none"
                            @click="showTikaJson = !showTikaJson"
                          >
                            {{ showTikaJson ? 'Hide' : 'Show' }} Tika JSON
                          </v-btn>
                          <pre v-show="showTikaJson" class="json-block">{{ JSON.stringify(tikaMeta, null, 2) }}</pre>
                        </v-card-text>
                      </v-card>
                    </v-col>
                    <!-- Docling -->
                    <v-col cols="12" md="6">
                      <v-card variant="outlined" class="parser-box">
                        <v-card-title class="d-flex align-center gap-2">
                          <v-chip color="secondary" size="small">Docling</v-chip>
                        </v-card-title>
                        <v-card-text>
                          <div v-if="doclingMeta" class="metadata-summary mb-2">
                            <div class="text-caption text-medium-emphasis">Metadata</div>
                            <div v-if="doclingMeta.parser_name">
                              Parser: {{ doclingMeta.parser_name }}
                              <span v-if="doclingMeta.parser_version"> v{{ doclingMeta.parser_version }}</span>
                            </div>
                          </div>
                          <div v-else class="text-caption text-medium-emphasis mb-2">
                            No Docling metadata (may be disabled or failed gracefully).
                          </div>
                          <v-btn
                            variant="text"
                            size="small"
                            class="text-none"
                            @click="showDoclingJson = !showDoclingJson"
                          >
                            {{ showDoclingJson ? 'Hide' : 'Show' }} Docling JSON
                          </v-btn>
                          <pre v-show="showDoclingJson" class="json-block">{{ JSON.stringify(doclingMeta, null, 2) }}</pre>
                        </v-card-text>
                      </v-card>
                    </v-col>
                  </v-row>

                  <div v-if="parseResult.output_doc?.search_metadata" class="mt-4">
                    <v-card variant="outlined">
                      <v-card-title class="text-subtitle-1">Search metadata (Tika)</v-card-title>
                      <v-card-text>
                        <div class="text-caption">Title: {{ parseResult.output_doc.search_metadata.title || 'N/A' }}</div>
                        <div class="text-caption">Body length: {{ (parseResult.output_doc.search_metadata.body || '').length }} chars</div>
                        <v-btn
                          variant="text"
                          size="small"
                          class="text-none mt-1"
                          @click="showSearchMeta = !showSearchMeta"
                        >
                          {{ showSearchMeta ? 'Hide' : 'Show' }} full search metadata
                        </v-btn>
                        <pre v-show="showSearchMeta" class="json-block">{{ JSON.stringify(parseResult.output_doc.search_metadata, null, 2) }}</pre>
                      </v-card-text>
                    </v-card>
                  </div>

                  <div class="mt-4">
                    <v-btn
                      variant="text"
                      size="small"
                      class="text-none"
                      @click="showFullJson = !showFullJson"
                    >
                      {{ showFullJson ? 'Hide' : 'Show' }} full response JSON
                    </v-btn>
                    <pre v-show="showFullJson" class="json-block">{{ JSON.stringify(parseResult, null, 2) }}</pre>
                  </div>
                </template>
              </div>
            </template>
          </v-card-text>
        </v-card>
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vuetifyRenderers } from '@jsonforms/vue-vuetify'
import '@jsonforms/vue-vuetify/lib/jsonforms-vue-vuetify.css'

const renderers = Object.freeze([...vuetifyRenderers])

const rootPath = (import.meta.env.VITE_ROOT_PATH as string | undefined) ?? '/modules/parser'
const apiPath = (import.meta.env.VITE_API_PATH as string | undefined) ?? `${rootPath}/api`

const schema = ref<Record<string, unknown> | null>(null)
const schemaError = ref<string | null>(null)
const config = ref<Record<string, unknown>>({})
const formOpen = ref([0])
const selectedFiles = ref<File[]>([])
const selectedSample = ref<string | null>(null)
const sampleBlob = ref<Blob | null>(null)
const parsing = ref(false)
const parseResult = ref<Record<string, unknown> | null>(null)
const showTikaJson = ref(false)
const showDoclingJson = ref(false)
const showSearchMeta = ref(false)
const showFullJson = ref(false)

const sampleOptions = [
  { title: 'MagPi145.pdf', value: 'MagPi145.pdf' },
  { title: 'IRS F1040 (PDF)', value: 'irs_f1040.pdf' },
  { title: 'Apple FY25 (PDF)', value: 'apple_FY25_Q4_Consolidated_Financial_Statements.pdf' },
  { title: 'Attention paper (PDF)', value: 'attn_all_you_need_1706.03762v7.pdf' },
]

const hasFile = computed(() => {
  if (selectedFiles.value?.length && selectedFiles.value[0]) return true
  if (selectedSample.value && sampleBlob.value) return true
  return false
})

const tikaMeta = computed(() => {
  const doc = parseResult.value?.output_doc as Record<string, unknown> | undefined
  const parsed = doc?.parsed_metadata as Record<string, unknown> | undefined
  return parsed?.tika ?? null
})

const doclingMeta = computed(() => {
  const doc = parseResult.value?.output_doc as Record<string, unknown> | undefined
  const parsed = doc?.parsed_metadata as Record<string, unknown> | undefined
  return parsed?.docling ?? null
})

function getDefaultData(s: Record<string, unknown>): Record<string, unknown> {
  const props = s.properties as Record<string, Record<string, unknown>> | undefined
  if (!props) return {}
  const out: Record<string, unknown> = {}
  for (const [key, prop] of Object.entries(props)) {
    if (prop.default !== undefined) {
      out[key] = prop.default
    } else if (prop.type === 'object' && prop.properties) {
      const nested = getDefaultData(prop as Record<string, unknown>)
      if (Object.keys(nested).length > 0) out[key] = nested
    } else if (prop.type === 'array' && prop.default === undefined) {
      out[key] = []
    }
  }
  return out
}

const uischema = computed(() => {
  if (!schema.value?.properties) return undefined
  const props = schema.value.properties as Record<string, unknown>
  return {
    type: 'VerticalLayout',
    elements: Object.keys(props).map((key) => ({
      type: 'Control',
      scope: `#/properties/${key}`,
    })),
  }
})

async function loadSchema() {
  schemaError.value = null
  const url = `${apiPath}/parser/service/config/jsonforms`
  try {
    const res = await fetch(url)
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
    const json = await res.json()
    schema.value = json
    config.value = { ...getDefaultData(json), ...config.value }
  } catch (e) {
    schemaError.value = e instanceof Error ? e.message : 'Failed to load schema. Is the parser running?'
    schema.value = null
  }
}

function onConfigChange(event: { data?: Record<string, unknown> }) {
  if (event.data) config.value = event.data
}

function onFileSelected() {
  selectedSample.value = null
  sampleBlob.value = null
}

async function onSampleSelected(sample: string | null) {
  if (!sample) {
    sampleBlob.value = null
    return
  }
  selectedFiles.value = []
  const url = `${rootPath}/samples/${sample}`
  try {
    const res = await fetch(url)
    if (!res.ok) throw new Error(`${res.status}`)
    sampleBlob.value = await res.blob()
  } catch {
    sampleBlob.value = null
  }
}

async function parse() {
  let file: File | Blob | null = null
  let fileName = 'document'
  if (selectedFiles.value?.length && selectedFiles.value[0]) {
    file = selectedFiles.value[0]
    fileName = file.name
  } else if (selectedSample.value && sampleBlob.value) {
    file = sampleBlob.value
    fileName = selectedSample.value
  }
  if (!file) return

  parsing.value = true
  parseResult.value = null
  const url = `${apiPath}/parser/service/parse-file`
  const form = new FormData()
  form.append('file', file, fileName)
  form.append('config', JSON.stringify(config.value))

  try {
    const res = await fetch(url, { method: 'POST', body: form })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) {
      parseResult.value = { success: false, error: data.error || data.message || `${res.status} ${res.statusText}` }
    } else {
      parseResult.value = data
    }
  } catch (e) {
    parseResult.value = { success: false, error: e instanceof Error ? e.message : 'Request failed' }
  } finally {
    parsing.value = false
  }
}

function clearResults() {
  parseResult.value = null
  showTikaJson.value = false
  showDoclingJson.value = false
  showSearchMeta.value = false
  showFullJson.value = false
}

onMounted(() => loadSchema())
watch(selectedSample, () => {
  if (selectedSample.value) onSampleSelected(selectedSample.value)
})
</script>

<style scoped>
.jsonforms-container {
  min-height: 120px;
}
.json-block {
  background: rgb(var(--v-theme-surface-variant));
  padding: 12px;
  border-radius: 8px;
  overflow: auto;
  font-size: 0.85em;
  max-height: 400px;
}
.metadata-summary {
  font-size: 0.9em;
}
</style>
