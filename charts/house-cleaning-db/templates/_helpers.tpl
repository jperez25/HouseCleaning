{{- define "hc-db.secretName" -}}
{{- default (printf "%s-credentials" .Chart.Name) .Values.auth.existingSecret -}}
{{- end -}}
