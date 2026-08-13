# Ink Review Atelier production rules

- `research/ui-design.md` v1.0 is the visual contract for the current redesign.
- Keep semantic product, themed shell, and ambient decoration as separate planes. Ambient code never owns business data and always uses `pointer-events: none`.
- Breakpoints are desktop `>=1280`, tablet `768–1279`, mobile `<=767`, with narrow polish at `<=560`.
- Closed responsive drawers must use both `inert` and `aria-hidden`; opening moves focus inside, Escape/scrim closes, Tab loops, and focus returns to the trigger.
- Mobile AnnotationRail entry lives in the sticky topbar and is at least 44×44px.
- Diff preserves readable code and uses local horizontal scrolling; never create page-level horizontal overflow.
- Use one RAF-bounded pointer observer. Static/reduced/coarse/hidden/unfocused states stop Canvas RAF and ambient CSS animation while preserving all content/actions.
- Approval is a destructive/important action: require a modal confirmation with focus trap and focus return.
- Ink visual rules and shared component classes live in `src/tokens.css`; pages must not invent near-duplicate colors, blur, radius, displacement or motion values.
