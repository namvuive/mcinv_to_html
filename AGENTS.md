# AGENTS.md

Client-only Fabric mod (Minecraft 1.21.11). Adds an **Export** button to container screens and
writes a single self-contained HTML snapshot to `.minecraft/mcinvtohtml/`. There is no server code,
no automated test suite, and no CI.

## Build / run

- Windows host: use `.\gradlew.bat`. Build with `.\gradlew.bat build` → `build/libs/mcinvtohtml-1.0.0.jar`.
- Dev client: `.\gradlew.bat runClient` (launches a real game; GUI cannot be automated here, so
  export + browser checks are manual).
- Need MC sources? `.\gradlew.bat genSources` (decompiled sources are not in the repo).
- Toolchain is pinned in `gradle.properties` (MC `1.21.11`, Loom `1.14.10`, Fabric Loader `0.20.1`,
  Fabric API `0.141.6+1.21.11`, Java 21). `build.gradle` uses `loom.officialMojangMappings()`.
  Do not hand-edit dependency versions inside `build.gradle`.
- Mod version lives in `gradle.properties` (`version`); `fabric.mod.json` injects it via
  `processResources` → `${version}`. Keep them in sync.

## Architecture (the non-obvious wiring)

Export flow: `AbstractContainerScreenMixin` (adds button in `init`) → `ExportManager.request`
(snapshots data immediately, sets `pending`) → `InventoryExporter.export` builds JSON →
next frame, `GameRendererMixin` TAIL calls `ExportManager.onFrameRendered` →
`Screenshot.takeScreenshot` on the main render target → crop to `leftPos/topPos/imageWidth/imageHeight`
(scaled by framebuffer/GUI ratio) → base64 PNG → `HtmlWriter` writes the file.

- **Capture suppression**: `AbstractContainerScreenCaptureMixin` cancels tooltip / carried item /
  slot highlight while `ExportManager.isCapturePending()`. `pending` must only be cleared in the
  `finally` block after scheduling the screenshot, not before — otherwise suppressed UI leaks into
  the capture.
- **Protected field access**: `leftPos`, `topPos`, `imageWidth/Height`, label offsets and
  `playerInventoryTitle` are protected. Always read them through
  `mixin/AbstractContainerScreenAccessor`. Direct access or reflection does not compile / is avoided.
- **Mixin registration**: every new mixin must be added to
  `src/main/resources/mcinvtohtml.client.mixins.json` under the `client` array, or it silently does
  nothing.
- **HTML template**: `assets/mcinvtohtml/templates/viewer.html` is loaded by classpath in
  `HtmlWriter` and injected at the `__MCINV_DATA__` placeholder. `HtmlWriter` escapes `</` to
  `<\\/` to protect the `<script>` block — keep that placeholder and escaping intact.

## viewer.html (single-file viewer — high-risk area)

- Must stay fully self-contained: no CDNs, no external assets; the panel is an inline base64 PNG and
  all data is in the injected `DATA` object.
- The SNBT/JSON highlighter is a **single-pass regex tokenizer** (`SNBT_TOKEN_RE`). Do not replace it
  with a hand-written char/index loop — a previous version had an infinite-loop bug on `byte[n]` /
  `int[n]` / `list[n]` typed-array syntax that froze the browser. Any loop touching `text` must
  guarantee the index advances.
- `HIGHLIGHT_LIMIT` (150 KB): above it the tab renders plain `textContent`; "Copy all" must still
  copy the full original string.
- Validate template edits before building: extract the `<script>` body and run `node --check` on it.
  Inline `DATA` is JSON; keep the injected payload valid and escape-aware.
- UI is intentionally minimal: white background, just the GUI, plus one line below it showing
  `screenClass · export time`. Do not reintroduce headers/footers/branding.

## Conventions / gotchas

- Java uses tab indentation and 1.21.11 Mojang names: `net.minecraft.resources.Identifier`
  (not `ResourceLocation`), `GuiGraphics` with `Matrix3x2fStack` `pose()`, screenshot API
  `Screenshot.takeScreenshot(RenderTarget, Consumer<NativeImage>)`,
  `NbtUtils.prettyPrint(tag, withBinaryBlobs)`. Verify any unfamiliar API against
  `genSources` output before assuming old mappings.
- Item stacks: tooltip is built from `ItemStack#getTooltipLines(TooltipContext, Player, TooltipFlag)`
  (instance method); full data via `DataComponentPatch.CODEC` + `RegistryOps`, SNBT via
  `NbtUtils.prettyPrint` wrapped as `{id, count, components}`.
- No tests, no lint task. The practical gate is `.\gradlew.bat build` plus `node --check` on the
  viewer script; then manual `runClient` export verification.
- `build/`, `.gradle/`, `run/`, and `mcinvtohtml/` (export output) are gitignored — never commit
  build artifacts or exported HTML.
- README screenshots live in `docs/` (`exported-inventory.jpg`, `item-data-modal.jpg`); update them
  when the viewer UI changes.
