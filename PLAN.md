# MCInv to HTML — Plan & Progress

## Goal

Fabric client-side mod for **Minecraft 1.21.11** named **MCInv to HTML** (modid `mcinvtohtml`,
package `com.namvuive.mcinvtohtml`).

Adds an **"Export"** button to every container screen (all `AbstractContainerScreen`
subclasses, excluding the Creative inventory). Clicking it writes a **single self-contained
HTML file** to `.minecraft/mcinv/` that reproduces the inventory exactly as seen in game:

- Inventory title (the framed GUI title)
- Every slot layout + items (count, icons rendered like the game, enchant glint included)
- Player inventory section shown below the container (full screen, as in game)
- Hover tooltip replica (name, lore, enchantments, attributes, durability, etc.)
- Click item -> modal with full item data components as syntax-highlighted SNBT,
  selectable text, Ctrl+C works, "Copy all" button
- Click title -> modal with full title component JSON, same highlighting + Copy all
- Read-only, offline, no CDN dependencies

## Key decisions (fixed)

| Topic | Decision |
|---|---|
| MC version | 1.21.11 (Dec 2025) |
| Mappings | Mojang official (mojmap) |
| Loader | Fabric Loader 0.20.1 |
| Fabric API | 0.141.6+1.21.11 |
| Java | 21 |
| Button | Text "Export", right of title bar, always visible (even with carried item) |
| Creative screen | Excluded |
| Carried item | Ignored on export (slots only) |
| Enchanted items | Glint baked in by rendering through the game's GUI item renderer |
| Filename | `{inv}_{yyyy-MM-dd_HH-mm-ss}.html` (sanitized) |
| Feedback | Chat message only (no config screen) |

## File layout

```
src/main/java/com/namvuive/mcinvtohtml/
  MCInvToHtmlClient.java                    client initializer (MOD_ID, LOGGER)
  mixin/AbstractContainerScreenMixin.java   adds "Export" button in init
  mixin/AbstractContainerScreenCaptureMixin.java  suppresses tooltip/carried/highlight while capturing
  mixin/AbstractContainerScreenAccessor.java      protected fields accessor
  mixin/ScreenAccessor.java                 invoker for addRenderableWidget
  mixin/GameRendererMixin.java              TAIL of render -> capture trigger
  export/InventoryExporter.java             collects title, slots, components -> JSON + SNBT
  export/ExportManager.java                 capture orchestration, crop, write, chat feedback
  export/HtmlWriter.java                    loads viewer template + injects JSON
src/main/resources/
  fabric.mod.json
  mcinvtohtml.client.mixins.json
  assets/mcinvtohtml/icon.png
  assets/mcinvtohtml/templates/viewer.html
gradle.properties / build.gradle / settings.gradle / gradle wrapper
```

## Checklist

- [x] Create this PLAN.md
- [x] Verify toolchain (Java 21, Gradle wrapper, Loom version)
- [x] Project skeleton compiles (`gradlew build`)
- [x] Export button mixin (exclude creative, always visible)
- [x] Data collection: title component JSON
- [x] Data collection: slots + full data components -> SNBT
- [x] Data collection: tooltip lines (colored segments) computed client-side
- [x] Item icons + glint captured (screenshot-based panel, pixel exact)
- [x] HTML writer to `.minecraft/mcinv/` + chat feedback
- [x] HTML viewer: layout replica, hover tooltip, item modal, title modal,
      syntax highlighting, selection + Ctrl+C, Copy all, toast
- [x] `gradlew build` passes
- [x] Client launches with mixins applied (`gradlew runClient` reaches main menu, no crash)
- [x] Fix: browser freeze on item click caused by infinite loop in `highlightSnbt`
      (typed-array `[` branch missing index advance). Rewrote highlighter as a
      single-pass regex tokenizer; added 150KB plain-text render guard.
- [ ] Manual in-game verification: export from each container type + open HTML in browser

## Verification plan

1. `gradlew build` (compile + jar)
2. `gradlew runClient`: open chest / furnace / hopper / shulker / dispenser / player
   inventory, export each; verify file written.
3. Attributes: enchanted item (glint visible), named/lored item, damaged item,
   unbreakable, count > 1, empty slots.
4. Export while holding an item on the cursor -> carried item ignored.
5. Open the HTML offline in browser: layout, hover tooltip, click item/title modals,
   syntax coloring, select + Ctrl+C, Copy all, toast.
6. Confirm Creative inventory shows no Export button.
