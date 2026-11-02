# API REST : obligatoirement RESTful

Toute API REST du projet respecte les contraintes REST (niveau 3 du modèle de maturité de Richardson visé). Toute entorse est justifiée à Adrien et consignée dans les « Décisions techniques » du `CLAUDE.md`.

- **Ressources, pas d'actions** : URIs = noms au pluriel, minuscules kebab-case, hiérarchiques (`/orders`, `/orders/{orderId}`, `/orders/{orderId}/items`). Aucun verbe (`/getOrders`, `/createUser`, `/orders/{id}/cancel` interdits). Une opération métier non CRUD devient une ressource (`POST /orders/{id}/cancellations`) ou un changement d'état (`PATCH`).
- **Sémantique HTTP** : `GET` sûr et idempotent, sans effet de bord ; `POST` création → `201 Created` + `Location` ; `PUT` remplacement complet, idempotent ; `PATCH` partiel (JSON Merge Patch ou JSON Patch, bon `Content-Type`) ; `DELETE` idempotent → `204`.
- **Codes de statut précis** : `200`, `201`, `202` (asynchrone), `204`, `304`, `400`, `401`, `403`, `404`, `405`, `406`, `409`, `412`, `415`, `422`, `429`, `5xx`. Jamais de `200` avec une erreur dans le corps.
- **Erreurs** en **Problem Details** (RFC 9457, `application/problem+json`), homogènes, sans fuite interne (stack traces).
- **Sans état** : aucune session serveur ; chaque requête porte son authentification (`Authorization: Bearer …`).
- **Hypermédia (HATEOAS)** : liens `self`, relations, actions possibles, pagination, dans un format standard (HAL via Spring HATEOAS…), pour que le client navigue sans construire les URIs.
- **Collections** : pagination (`page`/`size` ou curseur), filtre et tri par query params ; métadonnées et liens `first`/`prev`/`next`/`last`.
- **Négociation de contenu** : respecter `Accept` / `Content-Type` (`406` / `415` sinon).
- **Cache et concurrence** : `ETag` + `If-None-Match` (→ `304`) en lecture, `If-Match` pour le verrouillage optimiste en écriture (→ `412`) ; `Cache-Control` adapté.
- **Idempotence des créations** rejouables : `Idempotency-Key` sur les `POST` sensibles.
- **Versionnement** selon la stratégie retenue au cadrage ; aucune rupture sans nouvelle version.
- **Contrat OpenAPI** à jour avec le code ; **tests d'intégration** vérifiant méthodes, codes de statut, en-têtes (`Location`, `ETag`…) et format des erreurs.

## Application à Bergacast

Choix du projet (le `CLAUDE.md` résume, ce fichier détaille ; entorses et leurs raisons : « Décisions techniques » du `CLAUDE.md`).

- **Versionnement** : préfixe `/api/v1`. **Hypermédia** : HAL (`application/hal+json`, Spring HATEOAS, `RepresentationModelAssembler`).
- **Chemins** centralisés dans `web/ApiPaths` (mappings, liens, règles de sécurité). Ressources :
  - `GET /api/v1` : racine publique, `version` du serveur + liens ; `POST /api/v1/access-tokens` (public, → `200` ; `429` + `Retry-After` après 3 échecs consécutifs pour l'identifiant, `security/LoginThrottle`) ;
  - `/api/v1/podcasts[/{slug}]`, `/api/v1/podcasts/{slug}/api-tokens[/{id}]`, `/api/v1/admin-users[/{id}]` ;
  - `/api/v1/podcasts/{slug}/image` : `PUT` corps brut `image/jpeg|png` → `201` + `Location` ou `200`, `GET`, `DELETE` ; ETag `"sha256"` ; lien `image` toujours présent sur le podcast ;
  - `POST /api/v1/podcasts/{slug}/feed-publications` (admin, sans corps) : réécrit le flux → `200` + `{feedUrl, publishedAt, episodeCount}` ; lien `feedPublications` sur le podcast ;
  - `/api/v1/{slug}/episodes[/{id}]` : jeton du podcast ou admin pour `GET`/`POST` (multipart, partie `metadata` + audio) ; `PATCH`/`DELETE` admin. `{slug}` exclut `podcasts|admin-users|access-tokens` (`ApiPaths.EPISODES_MAPPING`) et les slugs réservés (`podcast/Slugs`).
- **Hors API** (publics) : `/robots.txt` (`src/main/resources/static/robots.txt`, seul fichier statique servi, `Disallow: /`, `max-age` 1 jour) ; anciennes URL des médias `GET|HEAD /{slug}.xml`, `/{slug}/image.{jpg|png}`, `/{slug}/episodes/{id}/audio.{ext}` → `301` vers le même chemin sous `BERGACAST_MEDIA_BASE_URL` (`web/MediaRedirectController`, sans base) ; `/v3/api-docs`, `/swagger-ui.html`.
- **Taille** : corps JSON (`application/json`, `*+json`) limités à 256 Kio par `web/JsonRequestSizeFilter` (`413`, aussi sans `Content-Length`), documenté par `OpenApiConventions` ; audio et image ont leurs propres limites.
- **PATCH** : JSON Merge Patch (`application/merge-patch+json`, `web/JsonMergePatch`). **Validation** → `422` avec `errors[{field, message}]`. **`If-Match`** optionnel, discordant → `412` ; **`If-None-Match`** → `304`. ETags forts via `web/EntityTags`.
- **Idempotence** : `Idempotency-Key` sur la publication d'épisode (unique par podcast, rejeu → même épisode).
- **Sécurité** : JWT admin ou jeton d'API Bearer lu uniquement sous `/api/` ; non authentifié → `401` + `WWW-Authenticate: Bearer` ; jeton d'un autre podcast → `403`.
- **CORS** sur `/api/**` seulement (`CorsFilter` + `security/ProblemDetailsCorsProcessor` : rejet en `403` Problem Details) ; origines `bergacast.cors.allowed-origins`, méthodes dont `PUT`, sans credentials ; en-têtes autorisés dans `web/ApiHeaders` (`Authorization`, `Content-Type`, `Accept`, `If-Match`, `If-None-Match`, `Idempotency-Key`) ; expose `ETag` et `Location`.
- **Documentation OpenAPI** (springdoc, publique, lintée par Spectral `.spectral.yaml` en CI) :
  - `@Schema(description, example, defaultValue, nullable)` sur chaque champ (springdoc ignore la Javadoc), summary + description sur chaque opération, description sur chaque paramètre ;
  - conventions communes ajoutées par `config/OpenApiConventions` (401/403 selon `security`, 404 si paramètre de chemin, `Location` sur 201, `If-None-Match` si 304, Problem Details) ; `ETag` via `@EntityTagged` (`web/`) ; `503` via `web/StorageResponses` sur chaque opération qui réécrit le flux ;
  - vérifié par `config/OpenApiDocumentationIntegrationTest`.
- **Tests** : un test d'intégration (`support/IntegrationTest`, `ApiTestClient`) par endpoint, succès et erreurs.
