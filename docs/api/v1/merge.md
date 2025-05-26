# API: Merge Multiple PDFs

This API merges several PDF files into one document. In addition to uploading files, you can provide remote URLs using the `urlInputs` field.

## Endpoint

```
POST /api/v1/general/merge-pdfs
```

## Parameters

- `fileInput` – PDF files to merge (optional when `urlInputs` is used).
- `urlInputs` – list of HTTP(S) links to PDFs. Separate URLs with newline characters.
- `removeCertSign` – `true` or `false` to specify whether signatures should be removed.
- `sortType` – method for sorting files (defaults to `orderProvided`).

## cURL Example

```bash
curl -X POST \
  -F "urlInputs=https://example.com/a.pdf\nhttps://example.com/b.pdf" \
  -F "removeCertSign=true" \
  https://your-instance/api/v1/general/merge-pdfs -o merged.pdf
```

The API returns the merged PDF in the response.

