{{- define "eventmesh.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "eventmesh.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "eventmesh.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "eventmesh.labels" -}}
app.kubernetes.io/name: {{ include "eventmesh.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
