# Sketcher — Implementation Plan

A desktop art-practice app (gesture drawing, memory drawing, reference study) built on a
locally-managed, tagged image library.

**Stack today:** Java 21, JavaFX 21.0.2, Maven. Three classes in the default package
(`AppRunner`, `App`, `ImageLoader`) implementing a directory-picker slideshow with a fixed
interval timer.

**Where this plan takes it:** a managed library (imported copies + SQLite metadata),
hierarchical tags with AND/OR/NOT filtering, a gallery for browsing and curation, a single
unified practice-session model, reusable presets for one-click start, and a portable
export/import archive.

---

## 1. Core design decisions

These are the decisions everything else hangs off. Getting them right up front avoids
rewrites later.

### 1.1 The library is a folder the user picks; the database lives inside it

One user-chosen **library root**. Everything the app owns lives under it:

```
<library-root>/
  sketcher.db          SQLite: all metadata
  images/ab/cd/<sha256>.<ext>    content-addressed originals (2-level fanout)
  thumbs/ab/cd/<sha256>.jpg      generated thumbnail cache (disposable)
  recycle/<sha256>.<ext>         soft-deleted originals
  exports/                       archives written by the export feature
```

Rationale:

- **Content-addressed filenames (SHA-256 of file bytes) make duplicate detection free.**
  "Does this image already exist?" becomes a primary-key lookup, not a filesystem scan.
  Also means two imports of the same picture can never collide or silently overwrite.
- **DB inside the library root** means the library is self-contained: point a second
  machine at a synced folder and everything (tags, favorites, presets, last-seen history)
  comes along. This is also what makes export/import a convenience rather than a
  necessity.
- The 2-level hex fanout (`images/ab/cd/…`) keeps directory sizes sane at 10k+ images.
- The original filename is kept in the DB (`original_name`) for display and search; the
  on-disk name is the hash. Users browse via the Gallery, not the file manager.

The library root path itself is stored outside the library, in app config
(`~/.config/sketcher/config.json` on Linux, `%APPDATA%` on Windows, `~/Library/Application
Support` on macOS), along with a recent-libraries list and window geometry.

### 1.2 SQLite for everything else

Add `org.xerial:sqlite-jdbc`. Plain JDBC with a thin DAO layer — no ORM; the schema is
small and the queries (especially tag filtering) are hand-written anyway.

- `PRAGMA foreign_keys = ON`, `journal_mode = WAL`, `synchronous = NORMAL`.
- A `schema_version` table plus ordered migration scripts in
  `src/main/resources/db/migrations/V001__init.sql`, applied on open. Needed from day one
  because the library files outlive any single app version.
- All writes in explicit transactions; batch imports in one transaction per batch.

### 1.3 One session model for all practice modes

Playlist, queue, random, and favorites are the same thing with different parameters. A
**session** is an ordered list of **segments**. Each segment has:

| Field | Values |
| --- | --- |
| `source` | a tag filter, a playlist, or (filter shorthand) favorites |
| `count` | N images, or unlimited |
| `order` | random or sequential (sequential = playlist order, or import order for filters) |
| `seconds_per_image` | 0 = untimed, or any duration |
| `tick_behavior` | advance immediately / break for N seconds / wait for click |
| `memory_mode` | off, or `{ show_seconds, hide_seconds }` |

Everything in the notes falls out of this:

- **Random practice** = one segment, filter source, unlimited count, random order.
- **Favorites** = one segment with the `is:favorite` filter.
- **Playlist** = one segment, playlist source, sequential or random.
- **Queue** ("5 female, 5 male, 5 dog, ramping timer") = three segments, each with its own
  count and timer.

This is strictly less code than four parallel modes, and more powerful.

### 1.4 Filters are a typed AST, compiled to SQL

A filter is a tree of predicates, not a flat tag list — required for NOT, which is the most
important filter per the notes ("NOT nude").

Predicate types: `tag(id, includeDescendants)`, `favorite`, `nameContains(text)`,
`importedBetween(a, b)`, `notSeenSince(duration)`, `untagged`.
Combinators: `AND`, `OR`, `NOT`.

- Built in the UI by a small filter builder (rows of tag chips with include/exclude
  toggles, plus an advanced mode for nested groups).
- Serialized to JSON for storage in presets and session segments. Version the JSON.
- Compiled to a single SQL `SELECT` with `EXISTS` / `NOT EXISTS` subqueries — this keeps
  "NOT nude" correct and fast rather than filtering in Java after loading everything.

### 1.5 Hierarchical tags

`tag(id, name, parent_id)`, unique on `(parent_id, name)`. Selecting `animal` matches
`animal > dog > husky` by default (toggleable per predicate).

Descendant resolution via a recursive CTE:

```sql
WITH RECURSIVE sub(id) AS (
  SELECT :tagId
  UNION ALL
  SELECT t.id FROM tag t JOIN sub ON t.parent_id = sub.id
)
```

Guard against cycles when re-parenting a tag (reject if the new parent is a descendant).
Display tags by full path (`animal / dog / husky`) in pickers, short name on chips.

**No built-in tag vocabulary.** This is the deliberate trade-off from the notes: more
setup than apps that ship fixed male/female, nude/clothed tags, but fully customizable. To
soften the cold start, ship a few **optional starter tag sets** (figure study, animals,
environments) that the user can import from Settings with one click, and offer this on the
first-run screen. The user can also skip it entirely.

---

## 2. Database schema (initial)

```sql
image(
  id INTEGER PK, hash TEXT UNIQUE NOT NULL, ext TEXT NOT NULL,
  original_name TEXT, width INTEGER, height INTEGER, byte_size INTEGER,
  imported_at INTEGER NOT NULL,        -- epoch millis
  source_path TEXT,                    -- where it came from, informational
  favorite INTEGER NOT NULL DEFAULT 0,
  last_seen_at INTEGER,                -- denormalized from view_event for fast filtering
  seen_count INTEGER NOT NULL DEFAULT 0,
  deleted_at INTEGER                   -- non-null = in recycle bin
)
tag(id INTEGER PK, name TEXT NOT NULL, parent_id INTEGER REFERENCES tag(id), 
    created_at INTEGER, UNIQUE(parent_id, name))
image_tag(image_id REFERENCES image(id) ON DELETE CASCADE,
          tag_id REFERENCES tag(id) ON DELETE CASCADE, PRIMARY KEY(image_id, tag_id))

playlist(id INTEGER PK, name TEXT UNIQUE NOT NULL, created_at INTEGER)
playlist_item(playlist_id, image_id, position, PRIMARY KEY(playlist_id, image_id))

preset(id INTEGER PK, name TEXT NOT NULL, created_at INTEGER, last_used_at INTEGER,
       use_count INTEGER DEFAULT 0, pinned INTEGER DEFAULT 0)
segment(id INTEGER PK, preset_id REFERENCES preset(id) ON DELETE CASCADE, position INTEGER,
        source_kind TEXT,          -- 'filter' | 'playlist'
        filter_json TEXT, playlist_id REFERENCES playlist(id),
        item_count INTEGER,        -- NULL = unlimited
        order_mode TEXT,           -- 'random' | 'sequential'
        seconds_per_image INTEGER, -- 0 = untimed
        tick_behavior TEXT, break_seconds INTEGER,
        memory_show_seconds INTEGER, memory_hide_seconds INTEGER)

practice_session(id INTEGER PK, preset_id, started_at, ended_at, images_shown INTEGER)
view_event(id INTEGER PK, image_id REFERENCES image(id) ON DELETE CASCADE,
           session_id REFERENCES practice_session(id) ON DELETE SET NULL,
           seen_at INTEGER, shown_ms INTEGER)

setting(key TEXT PK, value TEXT)
```

Indexes: `image(favorite)`, `image(imported_at)`, `image(last_seen_at)`,
`image(deleted_at)`, `image_tag(tag_id)`, `tag(parent_id)`, `view_event(image_id)`.

`last_seen_at` / `seen_count` are denormalized on purpose: "only images not seen in the
last 30 days" is a filter predicate that must be cheap, and recomputing it from
`view_event` on every query would not be.

---

## 3. Package structure

Move off the default package (required before this grows):

```
com.sketcher
  Launcher                     main(); replaces AppRunner
  SketcherApp                  JavaFX Application; owns navigation shell
  config/     AppConfig, ConfigStore, LibraryRef
  db/         Database, Migrations, dao/{ImageDao,TagDao,PlaylistDao,PresetDao,ViewDao}
  model/      Image, Tag, Playlist, Preset, Segment, SessionSpec, ImportCandidate
  library/    LibraryService, ImportService, HashService, ThumbnailCache,
              RecycleBin, ArchiveExporter, ArchiveImporter
  filter/     Filter (AST), FilterCodec (JSON), FilterCompiler (-> SQL), FilterSummary
  practice/   SessionBuilder, SessionEngine, SegmentRunner, PracticeClock
  ui/         Shell, Nav
      home/       HomeView, PresetCard
      gallery/    GalleryView, GridPane, ListPane, ImageDetailView, FilterBar
      imports/    ImportView, StagingTable
      tags/       TagManagerView, TagPicker, TagChip
      practice/   PracticeView, PracticeHud, SessionSetupView
      settings/   SettingsView, HelpView, RecycleBinView
      common/     Icons, Dialogs, AsyncTask, Theme
  resources/  style.css, db/migrations/*.sql, help/*, tagsets/*.json
```

`ImageLoader`'s shuffle-a-directory logic is superseded by DB-driven queues and goes away.
Its format sniffing moves into `ImportService`.

Threading rule: all file I/O, hashing, thumbnailing, and queries off the FX thread (a
single-threaded executor for DB work, a small pool for image decode); UI updates marshalled
back via `Platform.runLater`. Wrap this in `ui/common/AsyncTask` so it is consistent.

---

## 4. Features

### 4.1 Import

**Flow:** pick files or a folder (with an *include subfolders* checkbox) → files land in a
**staging table** → review → confirm.

Staging columns: thumbnail, filename, dimensions, size, status, tags.

Per-candidate status, computed while staging (background, incremental):

- **New** — ready to import.
- **Already in library** — hash matches an existing `image`. Warned, row flagged, excluded
  from import by default (with the option to re-enable, which just adds the batch tags to
  the existing image instead of re-copying).
- **Duplicate within this batch** — same hash twice in the selection; keeps the first.
- **Unreadable / unsupported** — flagged with the reason, cannot be imported.

**Import options (apply to the whole batch):**

- Add tag(s) to everything in the batch (tag picker with create-on-the-fly).
- **Copy** (default) or **Move** the source files.
- Remove any row from the staging list before confirming.
- Per-row tag overrides on top of the batch tags (cheap to add once the picker exists).

**Per file, on confirm:** stream-hash → if new, copy/move into `images/ab/cd/<hash>.<ext>`
→ probe dimensions → generate thumbnail → insert `image` + `image_tag` rows. Whole batch
in one transaction, with a progress dialog and a cancel that rolls back cleanly (copied
files for an aborted batch are removed).

**Format support:** JavaFX decodes PNG, JPEG, GIF, BMP natively — *not* WebP, AVIF, or
HEIC. The import filter will accept the native set at first and clearly reject the rest. If
WebP/AVIF matters, the fix is an ImageIO plugin (e.g. TwelveMonkeys) decoded to a
`BufferedImage` and converted with `SwingFXUtils` — tracked as an open question in §8,
since it adds `javafx-swing` plus dependencies.

### 4.2 Gallery

Everything in the library, as the primary management surface.

- **Grid view** (thumbnails, adjustable size) and **list view** (name, dimensions, tags,
  import date, last seen — sortable columns).
- **Filter bar:** tag filter with AND/OR/NOT, name search, import-date range, favorites
  only, untagged only, "not seen in…". Same filter engine as practice, same UI widget.
- **Open an image** for a full-size view with its metadata; arrow keys move through the
  current filtered set.
- **Favorite** toggle on hover in grid, on the row in list, and in the detail view.
- **Multi-select** (click, shift-click, ctrl-click, ctrl+A) with bulk actions: add tags,
  remove tags, favorite/unfavorite, delete, add to playlist.
- **Favorites tab** — the gallery with `is:favorite` pre-applied, not a separate screen.
- **Delete** moves to the recycle bin (§4.7).
- Virtualized rendering plus a bounded thumbnail cache; the gallery must stay smooth at
  10k+ images.

### 4.3 Tag management

A dedicated view with the tag tree:

- Create, rename, delete, and drag-to-reparent tags (cycle-checked).
- Shows image counts per tag, both direct and including descendants.
- Merge two tags; delete-with-reassign-children.
- Import a starter tag set (§1.5).

### 4.4 Practice

**Setup** builds a `SessionSpec` (§1.3): add segments, set each one's source, count, order,
timer, and tick behavior. A one-segment setup is the fast path and is what the home screen
uses; multi-segment is the "queue" power feature.

**Interval choices per segment:** not timed, 30s, 60s, 2m, 3m, 5m, 10m, custom. (The
current app's 10s option is worth keeping too — it is the usual warm-up.)

**Image count:** unlimited (default), 5, 10, 20, 50, custom.

**On timer expiry** (`tick_behavior`): advance immediately / break for N seconds, with a
countdown shown / wait for an explicit click.

**During a session:**

- Play/pause the timer, previous, next, exit session.
- Flip vertical, flip horizontal, grayscale toggle — all view-only transforms, reset per
  image (with a "keep transforms across images" toggle, since grayscale in particular is
  usually a whole-session preference).
- Zoom and pan: scroll or `+`/`-` to zoom, drag to pan, `0` to fit.
- A HUD showing time remaining, position (`7 / 20`), current segment, and the image's tags
  (tags hideable — for some practice you do not want to know).
- Tag and favorite the current image without leaving the session.

**Memory drawing mode** (per segment): show the image for N seconds → hide it (blank canvas
+ countdown) for N seconds → reveal it again for comparison, with the reveal held until
dismissed so you can check your work. Implemented as a three-phase state machine inside
`SegmentRunner`, not a separate mode.

**Seen tracking:** on each image shown, write a `view_event` and update
`image.last_seen_at` / `seen_count`. This powers the "only images not seen in the last X"
filter predicate, which is the main defense against practicing the same 30 pictures
forever.

**Keyboard shortcuts:**

| Key | Action |
| --- | --- |
| `Space` | pause / resume |
| `←` | previous |
| `→` | next |
| `Esc` | exit session (confirm if mid-session) |
| `F` | favorite current image |
| `G` | grayscale toggle |
| `H` / `V` | flip horizontal / vertical |
| `+` / `-` / `0` | zoom in / out / fit |
| `T` | tag current image |

Shortcuts are defined in one table in `PracticeView` so the help page can render them from
the same source rather than drifting.

### 4.5 Presets

A saved, named `SessionSpec`: its segments (filters/playlists, counts, orders) plus timers
and tick behavior. Create from the setup screen, or "save this as a preset" at the end of a
session. Stored in `preset` + `segment`. Tracks `last_used_at` and `use_count`.

### 4.6 Home screen

The notes are explicit that starting practice must be as frictionless as possible, so the
home screen is a launcher, not a dashboard:

- A row of large preset cards — pinned presets first, then most-recently-used — each one
  starting that session on a single click.
- A **Quick start** card (last used settings) for the zero-decision case.
- Secondary links: Import, Gallery, Tags, Settings, Help.
- Small library stat line (image count, tags, images practiced this week).
- **First run:** no library yet → prompt to choose a library folder, offer the starter tag
  sets, then point at Import.

### 4.7 Recycle bin

Deleting an image sets `deleted_at` and moves the file to `recycle/`. Metadata is retained,
so restore is lossless. A Recycle Bin view lists deleted items with their deletion date and
supports restore and permanent delete (plus "empty bin" and an optional auto-purge after N
days). Deleted images are excluded from every gallery and practice query — a single
`deleted_at IS NULL` clause that `FilterCompiler` always emits, so it cannot be forgotten.

### 4.8 Export / import archive

For moving between desktops without redoing the work.

**Export** writes a `.zip` (or a plain folder) containing `manifest.json` —
`{schema_version, exported_at, images[], tags[], playlists[], presets[], settings}` — and
the image files, named by hash. Options: whole library or current filter/selection;
include or omit practice history; include or omit thumbnails (regenerable, so omit by
default).

**Import** reads the manifest and merges: images already present by hash are skipped but
gain the archive's tags and favorite flag; tags are matched by full path and created as
needed; name-colliding presets and playlists are suffixed rather than overwritten. A
dry-run summary ("142 new images, 38 already present, 12 new tags") shows before anything
is written.

### 4.9 Settings and help

- Library folder (change / open recent / reveal in file manager).
- Default interval, default image count, default tick behavior.
- Thumbnail size, grid density, theme (dark default, light option).
- Recycle bin auto-purge.
- Starter tag sets.
- **Help page:** getting started, the tag hierarchy and filter semantics (AND/OR/NOT, why
  `NOT nude` works), the shortcut table, and the "links to online alternatives with images
  supplied" list from the README — useful on day one when the library is still empty.

---

## 5. Build order

Each phase is shippable and leaves the app working.

**Phase 0 — Foundation**
Move to `com.sketcher` packages; add `sqlite-jdbc`; `ConfigStore` + library-folder picker;
`Database` + migration runner + V001 schema; app shell with navigation; `AsyncTask`
threading helper. Existing slideshow stays reachable so nothing regresses.

**Phase 1 — Library and import**
`HashService`, `ImportService`, `ThumbnailCache`, the import view with staging, batch tags,
copy/move, remove-row, and duplicate warnings. *Milestone: images can be imported and
persist across restarts.*

**Phase 2 — Tags and filters**
Tag CRUD + hierarchy + reparenting; `Filter` AST, `FilterCodec`, `FilterCompiler`; the
filter builder widget. *Milestone: arbitrary AND/OR/NOT tag queries return correct sets
(unit-tested against a seeded DB).*

**Phase 3 — Gallery**
Grid and list views, filter bar, detail view, favorites, multi-select bulk actions.
*Milestone: the library is fully manageable without touching the filesystem.*

**Phase 4 — Practice**
`SessionSpec` / `SessionBuilder` / `SessionEngine` / `SegmentRunner`; practice view with
timer, navigation, flips, grayscale, zoom/pan, HUD, shortcuts; tick behaviors; view-event
logging. *Milestone: the old slideshow is deleted — this replaces it.*

**Phase 5 — Presets and home**
Preset CRUD, home screen launcher cards, quick start, first-run flow. *Milestone: practice
starts in one click.*

**Phase 6 — Advanced practice (done)**
Multi-segment queues, per-segment timer ramping, memory drawing mode, the
`notSeenSince` filter predicate, playlists.

**Phase 7 — Durability (done)**
Recycle bin, archive export, archive import with dry-run merge.

**Phase 8 — Polish (done)**
Help page, external-resource links, theming, empty states, error handling pass,
performance pass at 10k+ images, packaging (`jpackage` for a native bundle per OS).

- Theming: dark (default) / light toggle in Settings, applied live via `ui/common/Theme` —
  `style.css` (layout/light colors) plus a color-only `style-dark.css` overlay.
- Help page: added a "Need images to start with?" section for the README's "links to
  online alternatives" want-item, with placeholder URLs until real sites are chosen.
- Performance: the Gallery grid now virtualizes by row (a `ListView` of tile-rows instead
  of one giant `TilePane`), so tile nodes are only built for rows on screen — the fix for
  the Phase 8 "stay smooth at 10k+ images" risk. Not load-tested against an actual 10k+
  library in this pass.
- Error handling: FX-thread file-system actions (recycle/restore/permanent-delete) now go
  through `Dialogs.tryRun`, which shows an error dialog instead of crashing the UI thread.
- Packaging: `jpackage.sh` (`mvn package` + `jpackage`), verified end to end producing and
  launching a Linux `app-image`. Installer types that need platform tooling (`deb`/`rpm` on
  Linux, `msi` on Windows, `dmg`/`pkg` on macOS) are wired via the script's argument but
  untested here since those tools aren't installed in this environment.

---

## 6. Testing

- **Unit, no UI:** `FilterCompiler` (especially NOT and descendant expansion),
  `FilterCodec` round-trips, `SessionBuilder` segment expansion and counts, `HashService`,
  tag cycle rejection, migration application from empty and from each prior version.
- **Integration against a temp library:** import (new / duplicate / move vs copy / cancel
  rollback), delete → restore, export → import into an empty library produces an
  equivalent DB.
- **Manual smoke checklist per phase**, since JavaFX UI tests are expensive; add TestFX
  only if the practice view's state machine proves fragile.
- Seed a fixture generator producing a few hundred small images with a known tag tree —
  needed for both correctness and performance work.

## 7. Risks

- **Thumbnail/decode performance** on large imports and large images — mitigate with
  `Image(url, w, h, true, true)` for background-scaled decode, a disk thumbnail cache, and
  a bounded in-memory cache.
- **Filter-to-SQL complexity** for deeply nested groups — keep the AST small, cover it with
  tests first, and cap nesting depth in the builder UI.
- **Move-on-import is destructive** — a failed move must never lose the only copy; copy,
  verify the hash, then delete the source.
- **Library on cloud-synced storage** (the likely two-desktop setup) risks SQLite
  corruption from concurrent access. Document a single-machine-at-a-time rule; treat the
  export archive as the supported sync path.

## 8. Open questions

1. The practice notes list "zoom into image" followed by an empty bullet — what was the
   second item meant to be? (Candidates: a reference grid overlay, a mirror toggle, an
   image-only/fullscreen mode, a per-image note.)
2. WebP / AVIF / HEIC support — worth the extra decoder dependency, or is PNG/JPEG/GIF/BMP
   enough?
3. Animated GIFs and short video clips as reference material — in scope, or images only?
4. Should the same image be allowed to appear twice in one session (e.g. a 5-image filter
   with only 3 matches) — pad by repeating, or shrink the segment?
5. Multi-monitor / fullscreen presentation mode for practice — needed?
6. Is per-image tagging during import worth building in Phase 1, or are batch tags plus
   later gallery tagging enough?

---

*Notes this plan is derived from: the feature notes in this repo's history plus the README
"want" list (image tagging, show by tags, include-subfolders, persistent settings, modern
UI, help page, links to online alternatives).*
