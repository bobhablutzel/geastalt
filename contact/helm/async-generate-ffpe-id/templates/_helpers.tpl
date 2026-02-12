{{/*
Copyright (c) 2026 Bob Hablutzel. All rights reserved.

Licensed under a dual-license model: freely available for non-commercial use;
commercial use requires a separate license. See LICENSE file for details.
Contact license@geastalt.com for commercial licensing.
*/}}

{{/*
Expand the name of the chart.
*/}}
{{- define "async-generate-ffpe-id.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "async-generate-ffpe-id.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "async-generate-ffpe-id.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "async-generate-ffpe-id.labels" -}}
helm.sh/chart: {{ include "async-generate-ffpe-id.chart" . }}
{{ include "async-generate-ffpe-id.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "async-generate-ffpe-id.selectorLabels" -}}
app.kubernetes.io/name: {{ include "async-generate-ffpe-id.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
