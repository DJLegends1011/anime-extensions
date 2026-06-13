# Rule34World Image Counterpart Guide

This guide describes how to port the Anikku Rule34World video extension into a
Komikku/Mihon/Tachiyomi image extension.

## Target Repo

- Install index: `https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json`
- Source repo: `https://github.com/yuzono/tachiyomi-extensions`
- Existing source check: the current manga repo index does not appear to contain a
  Rule34World source yet.

Use the source repo, not the install index repo, for PR work.

## Contributing Notes

Read both contribution guides before opening PRs:

- Anime side: this repo's `CONTRIBUTING.md`
- Manga side: `yuzono/tachiyomi-extensions/CONTRIBUTING.md`

Important PR checklist items from the guides:

- Put the extension under `src/<lang>/<source>`.
- Use lowercase ASCII for the source folder, for example `src/en/rule34world`.
- Use package `eu.kanade.tachiyomi.extension.en.rule34world` on the manga side.
- Add `isNsfw = true` in `build.gradle`.
- Use generated launcher icons in the usual `mipmap-*` folders.
- Remove generated `web_hi_res_512.png`.
- Build/test the extension locally before PR.
- Fill out the AI-assisted PR checkbox truthfully after manual review.

## Module Skeleton

Recommended manga-side file layout:

```text
src/en/rule34world/
  AndroidManifest.xml
  build.gradle
  res/mipmap-*/ic_launcher.png
  src/eu/kanade/tachiyomi/extension/en/rule34world/
    Rule34World.kt
    Dto.kt
    Filters.kt
    Requests.kt
    Utils.kt
```

`build.gradle` should follow the manga repo convention:

```groovy
ext {
    extName = 'Rule34World'
    extClass = '.Rule34World'
    extVersionCode = 1
    isNsfw = true
}

apply plugin: 'kei.plugins.extension.legacy'
```

## API Facts To Reuse

Rule34World uses the same API for images and videos:

- Base API: `https://rule34.world/api/v2`
- Details: `GET /post/{id}`
- Root search: `POST /post/search/root`
- Hot search: `POST /post/search/hot/{days}`
- Site suggestions: `GET /post/suggestion/{id}` if needed

Live checks showed:

- Image posts use `type = 0`.
- Video posts use `type = 1`.
- Posted status uses `status = 2`.
- Search responses need `withTags = true` if local blacklist or suggestion logic
  needs tags.
- Image page URL should start with file key `10`, which maps to
  `{postId}.pic.jpg`.
- Cover/thumbnail should use preview keys such as `13` (`picpreview.jpg`) before
  falling back to `10`.

The same storage host selection logic from the video extension applies:

- If the post file key includes storage id `2`, prefer
  `https://rule34storage.b-cdn.net`.
- Otherwise use `https://rule34.world`.
- Path pattern:
  `/posts/{postId / 1000}/{postId}/{postId}.{filePart}`.

## Anime-To-Manga Type Mapping

Port the Anikku classes to their manga equivalents:

| Video extension | Image extension |
| --- | --- |
| `AnimeHttpSource` | `HttpSource` |
| `SAnime` | `SManga` |
| `SEpisode` | `SChapter` |
| `Video` | `Page` |
| `AnimesPage` | `MangasPage` |
| `AnimeFilterList` | `FilterList` |
| `fetchRelatedAnimeList` | `fetchRelatedMangaList` |

Typical manga-side imports:

```kotlin
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
```

## Post-As-Manga Model

Treat each Rule34World image post as one `SManga` with one synthetic chapter.

`SManga`:

- `url = post.id.toString()`
- `title = "Post #${post.id}"`. Do not build titles from the first few tags;
  tags are noisy and already appear in the app's genre/tag chips.
- `thumbnail_url = preview URL`
- `status = SManga.COMPLETED`
- `genre = grouped/clickable tags`
- `author = uploader or artist tags if desired`
- `description = posted/created/resolution/likes/uploader/sources`
- `update_strategy = UpdateStrategy.ONLY_FETCH_ONCE` if available and accepted by
  the repo conventions, because a post is immutable after fetch.

`SChapter`:

- Single chapter per post.
- `url = post.id.toString()`
- `name = post title`, for example `Post #1339948`.
- `chapter_number = 1F`
- Use posted/created date if the manga repo helper/date parser makes this simple.

`Page`:

- `pageListRequest(chapter)` should fetch `GET /post/{id}` or use a page URL that
  lets `pageListParse` fetch details.
- `pageListParse(response)` returns one page:

```kotlin
listOf(Page(index = 0, imageUrl = Rule34WorldUtils.imageUrl(post)))
```

## Search And Browse

Use the same filters as the video extension, but switch constants:

- `TYPE_IMAGE = 0`
- `STATUS_POSTED = 2`
- `SORT_LATEST = 0`
- `SORT_MOST_VIEWED = 2`

Keep `withTags = true` in all search request bodies so blacklist and suggestions
can inspect tag lists without extra detail requests.

Recommended tabs:

- Popular: root search, `sortBy = SORT_MOST_VIEWED`, `type = TYPE_IMAGE`.
- Latest: root search, `sortBy = SORT_LATEST`, `type = TYPE_IMAGE`.
- Search: same tag parser/filter model as video, but image type.
- Advanced filters: reuse Mode, Sort, Tags, Window if the manga app UI handles
  them cleanly.

## Tags

Reuse the current tag grouping model:

1. Copyright
2. Character
3. Artist
4. Resolution
5. Best Of
6. General
7. Meta
8. System

Keep clickable tags as individual genre entries, not grouped text blobs. Put the
grouped text form in description only if the app displays it nicely.

## Blacklist

Keep the extension setting:

- Key: `blacklist_tags`
- Input format: comma-separated tags.
- Matching: exact, case-insensitive normalized tag value.
- Apply to all browse/search/suggestion outputs.

This only works reliably when search requests include `withTags = true`.

## Suggestions

For image posts, use the manga-side suggestions hook:

```kotlin
override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga>
```

Recommended priority after the video extension tuning:

1. Character
2. Artist
3. Copyright

Do not use general tags for suggestions. General tags made results feel random.

Implementation outline:

1. Fetch current post details.
2. Build tag groups in character, artist, copyright order.
3. For each group, search up to a small number of tags from that group.
4. Merge candidates with a per-group cap and total cap.
5. Deduplicate by post id.
6. Exclude the current post.
7. Apply the blacklist.
8. Convert to `SManga`.

The API cannot split actual artist vs VA vs SFX because all of those arrive as
tag type `8`, so avoid overfitting with hardcoded artist subtype guesses.

## Relay To Video Version

Use this as the backport checklist for the Anikku video source after the
image-side fixes are accepted. Keep the video media constants on the video side
(`type = 1`), but mirror the behavior polish below.

- Post names: use a stable title such as `Post #1339948` for the video post and
  episode name. Do not build titles from the first few tags.
- Description tags: if the video UI already has a native tag/chip section, do
  not duplicate the full tag list in the description. Keep metadata and sources
  in description, and leave tags in the app's tag UI.
- Tag grouping: keep individual clickable tags ordered by Rule34World groups:
  Copyright, Character, Artist, Resolution, Best Of, General, Meta, System.
- Suggestion priority: custom suggestions should use Character, Artist, then
  Copyright. Do not use General tags for suggestions.
- Suggestion fetching: for each active group, use up to four tags from that
  group. Search the combined group first when there is more than one tag, then
  search each individual tag as fallback.
- Suggestion ranking: merge and dedupe candidates by post id, then rank by how
  many prioritized tags the candidate actually shares. A weak one-tag fallback
  should not appear before a post that also shares copyright or another
  prioritized tag.
- Suggestion limits: keep a total cap of `30`, and cap each active suggestion
  group at `ceil(totalCap / activeGroupCount)`. Do not refill sparse Artist
  slots with Character or Copyright posts; sparse groups can leave the final
  total below `30`.
- Suggestion filtering: exclude the current post, non-posted items, wrong media
  type, duplicate post ids, and local blacklist matches before returning
  suggestions.
- Direct URLs: full `https://rule34.world/post/{id}` links should resolve to a
  single post, including an optional trailing slash. Unsupported hosts, wrong
  paths, and free text should be ignored.

Useful image-side debug posts to mirror when testing the video source:

- `https://rule34.world/post/1339940` caught duplicated description tags.
- `https://rule34.world/post/1338422` and
  `https://rule34.world/post/1170599` caught out-of-order weak suggestion
  matches.
- `https://rule34.world/post/1339948` checked per-group suggestion caps where
  Artist was sparse but Character and Copyright had many candidates.

## URL Handling

Add an intent filter only if desired. If added:

- Match `https://rule34.world/post/...`.
- In `UrlActivity`, forward to Komikku/Mihon search using action
  `eu.kanade.tachiyomi.SEARCH`.
- In `fetchSearchManga`, detect full Rule34World post URLs and return the single
  matching post.

## Verification Checklist

Before opening the image-side PR:

- Confirm `type = 0` still returns image posts from the live API.
- Confirm `pic.jpg` works for several image posts.
- Confirm latest/popular/search do not return video posts.
- Confirm blacklist hides posts from latest, popular, search, and suggestions.
- Confirm URL search returns a single post.
- Confirm one image post creates one chapter and one page.
- Run the module test task if tests are added.
- Run `./gradlew src:en:rule34world:assembleDebug`.
- Manually install and open at least one post in Komikku/Mihon.
- Fill the PR checklist, including the AI-assisted/manual-review line.
