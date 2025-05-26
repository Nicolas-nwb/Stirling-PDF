# API: Fusionner plusieurs PDF

Cette API permet de fusionner plusieurs fichiers PDF en un seul document. Outre l'envoi de fichiers, il est possible de fournir des URLs distantes via le champ `urlInputs`.

## Endpoint

```
POST /api/v1/general/merge-pdfs
```

## Paramètres

- `fileInput` – fichiers PDF à fusionner (optionnel si `urlInputs` est utilisé).
- `urlInputs` – liste de liens HTTP(S) vers des PDF. Les URLs doivent être séparées par des retours à la ligne.
- `removeCertSign` – `true` ou `false` pour indiquer si les signatures doivent être supprimées.
- `sortType` – méthode de tri des fichiers (par défaut `orderProvided`).

## Exemple cURL

```bash
curl -X POST \
  -F "urlInputs=https://exemple.com/a.pdf\nhttps://exemple.com/b.pdf" \
  -F "removeCertSign=true" \
  https://votre-instance/api/v1/general/merge-pdfs -o fusion.pdf
```

L'API retournera le PDF fusionné dans la réponse.
