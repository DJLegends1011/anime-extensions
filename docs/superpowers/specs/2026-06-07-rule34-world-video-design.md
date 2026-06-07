# Rule34 World Video Extension Design

## Purpose

Add a foundation extension for Rule34 World at `https://rule34.world/` that supports videos only. The extension should make the site usable in Anikku/Aniyomi by listing video posts, searching video posts by tags, showing useful post metadata, and resolving direct MP4 URLs.

## Scope

The first version is intentionally direct and small:

- Create one English extension module at `src/en/rule34world`.
- Mark the module as NSFW.
- Use Rule34 World's JSON backend instead of scraping rendered HTML.
- Force every listing/search request to `type = 1`, which is the site's video post type.
- Return one episode per post.
- Resolve playable MP4 files from the site's deterministic file paths.

Out of scope for this foundation:

- Image post support.
- Login-only features such as likes, bookmarks, subscriptions, comments, uploads, or playlists.
- WebView-only scraping. WebView support may be added later as a fallback if the direct API changes or starts requiring browser state.

## Site Findings

Rule34 World is an Angular frontend with server-rendered hydration state. Its backend lives under `/api/v2`.

Relevant endpoints:

- `POST /api/v2/post/search/root` lists posts.
- `GET /api/v2/post/{id}` returns post details, tags, file metadata, and source links.
- `GET /api/v2/tag/search/{query}` returns tag suggestions.

Video-only search payload:

```json
{
  "take": 30,
  "type": 1,
  "sortBy": 0,
  "includeTags": [],
  "status": 2,
  "checkHasMore": true
}
```

Pagination is cursor-based. Responses include `items`, `cursor`, and `hasMore`; the next request sends the previous response cursor.

Sort values observed in the frontend:

- `0`: latest
- `1`: top rated / likes
- `2`: most viewed

Post type values observed in the frontend:

- `0`: image
- `1`: video

## Media URL Resolution

The frontend file resolver maps post file ids to deterministic filenames:

- `100`: `mov.mp4`
- `112`: `mov480.mp4`
- `113`: `mov720.mp4`
- `114`: `1080.mp4`
- `101`: `mov256.mp4` preview
- `102`: `mov256ex.mp4` preview
- `10`: `pic.jpg`
- `13`: `picpreview.jpg`
- `14`: `picsmall.jpg`

Relative path formula:

```text
/posts/{floor(postId / 1000)}/{postId}/{postId}.{filePart}
```

The site uses storage ids in each post's `files` map. Storage id `2` maps to Bunny CDN with pull zone `rule34storage`.

URL selection:

1. If the post exposes a direct file URL in `filesDirect`, use it.
2. If the selected file id has storage id `2`, use `https://rule34storage.b-cdn.net` plus the relative path.
3. Otherwise use `https://rule34.world` plus the relative path.

Verified examples:

- `https://rule34storage.b-cdn.net/posts/1337/1337088/1337088.mov720.mp4`
- `https://rule34.world/posts/1337/1337096/1337096.mov720.mp4`

Both returned `200` with `video/mp4`.

## Extension Behavior

Popular and latest should both use `POST /api/v2/post/search/root` with `type = 1`, `status = 2`, and `take = 30`. Popular should use `sortBy = 1`; latest should use `sortBy = 0`.

Search should split user text into tags and send them as `includeTags`. Filters should include a sort selector with latest, top rated, and most viewed. The first version does not need autocomplete UI, but the tag search endpoint is available for later refinement.

List items should derive a readable title from the first useful tags plus the post id, because the backend does not return a dedicated title. Thumbnails should prefer preview image file ids in this order: `13`, `14`, `10`.

Details should request `/api/v2/post/{id}` and populate:

- title from prominent tags and post id
- genre from all tags
- description with post id, duration, resolution, likes/views when present, and external source links when present
- status as completed

Episode list should return one `SEpisode` named `Video`, storing the numeric post id as the episode URL payload.

Video list should prefer normal MP4 variants in this order:

- `114` / 1080p
- `113` / 720p
- `112` / 480p
- `100` / original MP4

The extension may expose a preferred quality setting later, but the foundation can return every available normal MP4 variant and let Aniyomi sort/display them.

## Error Handling

If search returns no video posts, return an empty `AnimesPage` with `hasNextPage = false`.

If detail JSON is unavailable, use the list item metadata where possible and fail with a concise source error only when playback cannot be resolved.

If a post has no supported MP4 file ids, return an empty video list. Do not attempt image fallback in this video-only extension.

## Testing

Verification should include:

- Gradle compile/build for `src:en:rule34world`.
- A quick live API check for `POST /api/v2/post/search/root` with `type = 1`.
- A live HEAD or GET check for at least one generated MP4 URL.
- Manual code inspection against the existing extension conventions, especially `Rule34Video` and other NSFW single-video sources.

## Future WebView Support

WebView support is a follow-up fallback, not part of the foundation. If needed later, the extension can add a URL activity or WebView-assisted bootstrap to capture browser state, cookies, or frontend request behavior while still keeping normal playback on direct MP4 URLs.
