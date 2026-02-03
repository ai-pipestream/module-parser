<template>
  <v-app>
    <v-main>
      <v-container>
        <v-card class="mx-auto" max-width="1200">
          <v-toolbar color="primary" density="compact">
            <v-toolbar-title class="text-h6">
              Parser Test Dashboard
            </v-toolbar-title>
            <v-spacer></v-spacer>
            <v-btn icon="mdi-refresh" @click="fetchSchema" :loading="loading" variant="text"></v-btn>
          </v-toolbar>
          
          <v-card-text class="pa-4">
            <v-alert v-if="error" type="error" class="mb-4" density="compact">
              {{ error }}
            </v-alert>

            <!-- TEST SECTION AT THE TOP -->
            <v-card variant="outlined" class="mb-8 border-primary">
              <v-card-title class="text-subtitle-1 bg-primary-lighten-5 py-2">
                <v-icon start color="primary">mdi-file-find</v-icon>
                Document Processing Test
              </v-card-title>
              <v-card-text class="pa-4">
                <v-row>
                  <v-col cols="12" md="6">
                    <v-file-input
                      v-model="testFile"
                      label="Upload file"
                      prepend-icon="mdi-upload"
                      variant="outlined"
                      density="comfortable"
                      hide-details
                      @change="onFileSelected"
                    ></v-file-input>
                  </v-col>
                  <v-col cols="12" md="6">
                    <v-select
                      v-model="selectedSample"
                      :items="samples"
                      item-title="title"
                      item-value="filename"
                      label="Or choose from samples"
                      prepend-icon="mdi-library-books"
                      variant="outlined"
                      density="comfortable"
                      hide-details
                      clearable
                      @update:model-value="onSampleSelected"
                    >
                      <template v-slot:item="{ props, item }">
                        <v-list-item v-bind="props" :subtitle="item.raw.description"></v-list-item>
                      </template>
                    </v-select>
                  </v-col>
                </v-row>

                <div class="d-flex gap-4 mt-4">
                  <v-btn
                    color="primary"
                    :disabled="!canRun || parsing"
                    :loading="parsing && lastMode === 'direct'"
                    @click="runParse('direct')"
                    prepend-icon="mdi-play"
                  >
                    Direct Parse
                  </v-btn>
                  <v-btn
                    color="secondary"
                    :disabled="!canRun || parsing"
                    :loading="parsing && lastMode === 'test'"
                    @click="runParse('test')"
                    prepend-icon="mdi-flask"
                  >
                    Run as Test Process
                  </v-btn>
                  <v-btn variant="text" v-if="parseResult" @click="parseResult = null">Clear Results</v-btn>
                </div>

                <div v-if="parseResult" class="mt-6">
                  <v-divider class="mb-4"></v-divider>
                  <div class="d-flex align-center mb-2 flex-wrap">
                    <div class="text-subtitle-2">Response:</div>
                    <v-chip size="x-small" class="ml-2" :color="parseResult.success ? 'success' : 'error'">
                      {{ lastMode === 'direct' ? 'Direct' : 'Test Mode' }}
                    </v-chip>
                    <v-chip size="x-small" class="ml-2" color="info" v-if="parseResult._ui_timing_ms">
                      <v-icon start icon="mdi-clock-outline" size="small"></v-icon>
                      {{ parseResult._ui_timing_ms }} ms
                    </v-chip>
                  </div>
                  
                  <div class="json-wrapper rounded border pa-2 bg-grey-lighten-5">
                    <json-viewer :value="parseResult" :expand-depth="1" copyable boxed sort></json-viewer>
                  </div>

                  <!-- Any object view -->
                  <div v-if="parseResult.output_doc && parseResult.output_doc.parsed_metadata" class="mt-4">
                    <div class="text-caption font-weight-bold mb-2">Unpacked Metadata:</div>
                    <v-expansion-panels variant="popout" density="compact">
                      <v-expansion-panel
                        v-for="(meta, key) in parseResult.output_doc.parsed_metadata"
                        :key="key"
                      >
                        <v-expansion-panel-title class="text-capitalize py-1">
                          {{ key }} <span class="text-grey ml-2 text-caption">({{ meta.parser_name }} v{{ meta.parser_version }})</span>
                        </v-expansion-panel-title>
                        <v-expansion-panel-text>
                          <json-viewer :value="meta.data || meta" :expand-depth="2" copyable boxed sort></json-viewer>
                        </v-expansion-panel-text>
                      </v-expansion-panel>
                    </v-expansion-panels>
                  </div>
                </div>
              </v-card-text>
            </v-card>

            <v-divider class="mb-8"></v-divider>

            <!-- CONFIGURATION SECTION -->
            <div v-if="loading" class="text-center py-12">
              <v-progress-circular indeterminate color="primary" size="48"></v-progress-circular>
              <div class="mt-4 text-caption">Fetching ParserConfig schema...</div>
            </div>

            <template v-else>
              <div class="text-h6 mb-4 px-2">Configuration Strategy</div>
              <json-forms
                :data="data"
                :schema="schema"
                :uischema="uischema"
                :renderers="renderers"
                @change="onChange"
              />
              
              <v-expansion-panels class="mt-8">
                <v-expansion-panel>
                  <v-expansion-panel-title>Debug: Raw Configuration State</v-expansion-panel-title>
                  <v-expansion-panel-text>
                    <pre class="bg-grey-lighten-4 pa-4 rounded text-caption" style="max-height: 300px; overflow: auto;">{{ JSON.stringify(data, null, 2) }}</pre>
                  </v-expansion-panel-text>
                </v-expansion-panel>
              </v-expansion-panels>
            </template>
          </v-card-text>
        </v-card>
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vuetifyRenderers } from '@jsonforms/vue-vuetify'

const renderers = Object.freeze([...vuetifyRenderers])

const schema = ref(null)
const data = ref({})
const loading = ref(true)
const error = ref(null)

const samples = ref([])
const selectedSample = ref(null)
const testFile = ref(null)
const parsing = ref(false)
const parseResult = ref(null)
const lastMode = ref('direct')

const canRun = computed(() => {
  return testFile.value || selectedSample.value
})

const uischema = {
  type: "VerticalLayout",
  elements: [
    {
      type: "Group",
      label: "Parser Identity & Strategy",
      elements: [
        { type: "Control", scope: "#/properties/config_id" },
        {
          type: "HorizontalLayout",
          elements: [
            { type: "Control", scope: "#/properties/strategy" },
            { type: "Control", scope: "#/properties/enableTika" },
            { type: "Control", scope: "#/properties/enableDocling" }
          ]
        }
      ]
    },
    {
      type: "Group",
      label: "Core Extraction Controls",
      elements: [
        { type: "Control", scope: "#/properties/parsingOptions" },
        { type: "Control", scope: "#/properties/contentTypeHandling" }
      ]
    },
    {
      type: "Group",
      label: "Docling Engine (Advanced Analysis)",
      rule: {
        effect: "SHOW",
        condition: {
          scope: "#/properties/enableDocling",
          schema: { const: true }
        }
      },
      elements: [
        { type: "Control", scope: "#/properties/doclingOptions" }
      ]
    },
    {
      type: "Group",
      label: "Resilience & Layout",
      elements: [
        {
          type: "HorizontalLayout",
          elements: [
            { type: "Control", scope: "#/properties/errorHandling" },
            { type: "Control", scope: "#/properties/advancedOptions" }
          ]
        },
        { type: "Control", scope: "#/properties/outlineExtraction" }
      ]
    }
  ]
}

const onChange = (event) => {
  data.value = event.data
}

const onFileSelected = () => {
  selectedSample.value = null
}

const onSampleSelected = (filename) => {
  if (filename) testFile.value = null
}

// Derive API base from the Vite base URL (e.g., /modules/parser/admin/ -> /modules/parser/api)
const apiBase = import.meta.env.BASE_URL.replace(/\/admin\/$/, '/api')

const runParse = async (mode) => {
  lastMode.value = mode
  parsing.value = true
  parseResult.value = null
  
  const formData = new FormData()
  
  try {
    if (selectedSample.value) {
      // Fetch the sample from the static path (relative to root-path)
      // samples are at /modules/parser/samples/...
      // apiBase is /modules/parser/api
      // so root is apiBase without /api
      const rootPath = apiBase.replace(/\/api$/, '')
      const url = `${rootPath}/samples/${selectedSample.value}`
      const res = await fetch(url)
      if (!res.ok) throw new Error(`Could not load sample: ${res.status}`)
      const blob = await res.blob()
      formData.append('file', blob, selectedSample.value)
    } else {
      const file = Array.isArray(testFile.value) ? testFile.value[0] : testFile.value
      formData.append('file', file)
    }
    
    formData.append('config', JSON.stringify(data.value))

    const endpoint = mode === 'test' ? 'test-process' : 'parse-file'
    
    const startTime = performance.now()
    const response = await fetch(`${apiBase}/parser/service/${endpoint}`, {
      method: 'POST',
      body: formData
    })
    const endTime = performance.now()
    const duration = Math.round(endTime - startTime)
    
    if (!response.ok) throw new Error(`Server returned ${response.status}`)
    const json = await response.json()
    // Inject timing into the result for display
    parseResult.value = { ...json, _ui_timing_ms: duration }
  } catch (err) {
    console.error('Action failed:', err)
    parseResult.value = { error: err.message, success: false }
  } finally {
    parsing.value = false
  }
}

const fetchSamples = async () => {
  try {
    const res = await fetch(`${apiBase}/parser/service/demo/documents`)
    const json = await res.json()
    samples.value = json.documents || []
  } catch (e) {
    console.warn('Failed to fetch samples', e)
  }
}

const fetchSchema = async () => {
  loading.value = true
  error.value = null
  try {
    const response = await fetch(`${apiBase}/parser/service/config/jsonforms`)
    if (!response.ok) throw new Error(`Backend error: ${response.status}`)
    schema.value = await response.json()
  } catch (err) {
    console.error('Failed to load schema:', err)
    error.value = "Could not load schema. Is Quarkus running?"
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchSchema()
  fetchSamples()
})
</script>

<style scoped>
.gap-4 { gap: 16px; }
.bg-primary-lighten-5 { background-color: #f0f4ff; }
</style>