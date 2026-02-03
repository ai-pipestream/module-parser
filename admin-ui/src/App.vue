<template>
  <v-app>
    <v-main>
      <v-container>
        <v-card class="mx-auto" max-width="1200">
          <v-toolbar color="primary" density="compact">
            <v-toolbar-title class="text-h6">
              Parser Configuration
            </v-toolbar-title>
            <v-spacer></v-spacer>
            <v-btn icon="mdi-refresh" @click="fetchSchema" :loading="loading" variant="text"></v-btn>
          </v-toolbar>
          
          <v-card-text class="pa-4">
            <v-alert v-if="error" type="error" class="mb-4" density="compact">
              {{ error }}
            </v-alert>

            <div v-if="loading" class="text-center py-12">
              <v-progress-circular indeterminate color="primary" size="48"></v-progress-circular>
              <div class="mt-4 text-caption">Fetching schema...</div>
            </div>

            <template v-else>
              <json-forms
                :data="data"
                :schema="schema"
                :uischema="uischema"
                :renderers="renderers"
                @change="onChange"
              />
              
              <v-divider class="my-8"></v-divider>

              <v-card variant="outlined" class="mb-6">
                <v-card-title class="text-h6 bg-grey-lighten-4">
                  Test Parsing with this Config
                </v-card-title>
                <v-card-text class="pa-4">
                  <v-file-input
                    v-model="testFile"
                    label="Select document to parse"
                    prepend-icon="mdi-file-document"
                    variant="outlined"
                    density="comfortable"
                    show-size
                  ></v-file-input>
                  
                  <v-btn
                    color="secondary"
                    :disabled="!testFile || parsing"
                    :loading="parsing"
                    @click="runParse"
                    prepend-icon="mdi-play"
                  >
                    Run Parse
                  </v-btn>

                  <div v-if="parseResult" class="mt-6">
                    <div class="text-subtitle-2 mb-2">Parse Result:</div>
                    <pre class="bg-grey-darken-4 text-green-lighten-3 pa-4 rounded text-caption" style="max-height: 500px; overflow: auto;">{{ JSON.stringify(parseResult, null, 2) }}</pre>

                    <!-- Specialized view for Any data -->
                    <div v-if="parseResult.output_doc && parseResult.output_doc.parsed_metadata" class="mt-4">
                      <div class="text-subtitle-2 mb-2">Unpacked Metadata Objects:</div>
                      <v-expansion-panels variant="popout" density="compact">
                        <v-expansion-panel
                          v-for="(meta, key) in parseResult.output_doc.parsed_metadata"
                          :key="key"
                        >
                          <v-expansion-panel-title class="text-capitalize">
                            {{ key }} ({{ meta.parser_name }} v{{ meta.parser_version }})
                          </v-expansion-panel-title>
                          <v-expansion-panel-text>
                            <div v-if="meta.data && meta.data['@type']" class="mb-2 text-caption text-medium-emphasis">
                              Type: {{ meta.data['@type'] }}
                            </div>
                            <pre class="bg-grey-lighten-4 pa-2 rounded text-caption" style="overflow: auto;">{{ JSON.stringify(meta.data, null, 2) }}</pre>
                          </v-expansion-panel-text>
                        </v-expansion-panel>
                      </v-expansion-panels>
                    </div>
                  </div>
                </v-card-text>
              </v-card>
              
              <v-expansion-panels>
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
import { ref, onMounted } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vuetifyRenderers } from '@jsonforms/vue-vuetify'

const renderers = Object.freeze([...vuetifyRenderers])

const schema = ref(null)
const data = ref({})
const loading = ref(true)
const error = ref(null)

const testFile = ref(null)
const parsing = ref(false)
const parseResult = ref(null)

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

const runParse = async () => {
  if (!testFile.value) return
  
  parsing.value = true
  parseResult.value = null
  
  const formData = new FormData()
  // Vuetify v-file-input returns a single file or array of files. 
  // In V3 it's usually just the File object if multiple is false.
  const file = Array.isArray(testFile.value) ? testFile.value[0] : testFile.value
  formData.append('file', file)
  formData.append('config', JSON.stringify(data.value))

  try {
    const response = await fetch('/modules/parser/api/parser/service/parse-file', {
      method: 'POST',
      body: formData
    })
    if (!response.ok) throw new Error(`Parse failed: ${response.status}`)
    parseResult.value = await response.json()
  } catch (err) {
    console.error('Parsing error:', err)
    parseResult.value = { error: err.message }
  } finally {
    parsing.value = false
  }
}

const fetchSchema = async () => {
  loading.value = true
  error.value = null
  try {
    const response = await fetch('/modules/parser/api/parser/service/config/jsonforms')
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
})
</script>

<style>
</style>
