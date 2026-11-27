---
name: rc-research-and-explain
kind: process
description: "How to research a topic reliably, write prose that does not read as machine-made, and explain a system with a diagram or code map: entity resolution, reproducible searches, source tiers, citation checks, AI-writing tells, diagram gate and budget, deterministic structure with tagged inferences. Load for research, reports, docs, copy edits, architecture diagrams."
---
# Research, plain writing and diagrams (process, not facts)

> Distilled 2026-11 from public repos (MIT: mvanhorn/last30days-skill, blader/humanizer, Nutlope/hallmark, cathrynlavery/diagram-design, K-Dense-AI/scientific-agent-skills, Understand-Anything, codegraph; Apache-2.0: tt-a1i/archify, Graphify; methods only), rewritten in our own words. Jonathan's CLAUDE.md wins on conflict; for US English prose rules also load `rc-american-english-writing`.

## Do this

### Research
1. **Resolve the entities first** (people, handles, companies, tickers, product names, subreddits) before searching; a wrong-entity match is the usual silent failure. Write the plan down: question, entities, sources, time window.
2. **Reproducible searches.** Record every query string, source, date and hit count. Use two or more independent sources. A search you cannot repeat is not systematic.
3. **Source tiers.** Primary (official docs, the source code, the filing, the subject's own post) over secondary over opinion. Peer-reviewed, preprint and blog are different tiers; never present one as another. Rank by relevance and engagement, not by SEO position.
4. **Silence is not evidence.** "No results" and "the source failed, rate-limited or timed out" are different outcomes; say which. A cluster that fails a relevance check counts as absent.
5. **Records, reports, studies.** Deduplicate by DOI or title, link a preprint and its journal version to one study, and do not count overlapping samples twice.
6. **Citations must support the claim.** A real DOI or URL proves only that the page exists. Check identity, that it says what you claim, corrections and retractions. Never invent a volume, page, DOI or quote; drop anything you could not confirm. Report conflicting findings with their evidence quality instead of dropping the inconvenient one.
7. **Absence claims name what was checked** ("section 3 and the appendix do not mention X"). Method criticism names the design and the condition under which it would distort the result, marked as stated by the authors or inferred by you.
8. **Present:** conclusion first, then evidence; inline links next to each claim; at most a few short quotes; say what you could not verify. Current facts get a date.

### Writing that does not sound machine-made
9. **Mark the tells, then rewrite and re-check the facts.** One-sighting tells: "not X but Y" contrasts that only add weight; a one-line closer that restates the paragraph; deep-sounding sayings; staged run-ups ("here's the thing", "let's dive in"); arguing with a reader who is not there. Weak alone, notable in groups: forced triads, repeated sentence openings, stacked hedges, passive voice, inflated words ("pivotal", "seamless", "unlock"), vague "experts say", trailing "-ing" clauses, decorative bold and labelled bullets, dash overuse, chatbot leftovers ("I hope this helps"), "serves as" for "is".
10. **Edit lightly.** Keep every supported claim, the author's voice and structure, humor and useful contrast; fragments and parallelism are fine when deliberate. Leave informal text (chat, rants, angry reviews) alone unless unrelated tells pile up; the writer's emotion stays at full strength. Edit prose only: code, commands, data and link targets are untouched. Quotes, names and text that discusses a phrase are exempt.
11. **Never invent** numbers, testimonials, logos, quotes or case studies; use real material, label stand-ins, or cut the claim. Style cannot prove who wrote a text; edit for the reader, not to dodge a detector.

### Explaining a system
12. **Draw only when it earns its place:** would a reader learn more from the picture than from a paragraph or a table? Lists and attribute-only comparisons become tables; a one-box diagram is a sentence. Use Mermaid for diagrams that live in git and change often; spend design effort on diagrams you publish.
13. **Budget:** about 9 nodes, 12 arrows, 2 accent elements, 2 callouts per diagram; more means overview plus detail. Remove test: can any node, edge or label go? Merge nodes that always travel together; drop an edge the layout already implies. One accent color on the 1 to 2 focal elements; neutral elsewhere.
14. **Pick the type by the question:** architecture, workflow, sequence, dataflow, lifecycle, ERD (every real column, with type and key), class, tree, timeline. Lay out the reader's main path first, in order, branches beside the part that owns them; orthogonal connectors, labels on an opaque mask, no line passing behind an unrelated box. Walk the path at reading size and name one defect before calling it done. The static frame must carry every fact (no hover-only detail); SVG gets `role=img` and a title. Timings count as real only when they come from logs or traces; otherwise label them illustrative.
15. **Code maps.** Build structure with a deterministic parser (imports, definitions, calls); use a model only for summaries and grouping. Tag each edge as stated in the source or inferred; a path is only as sure as its weakest edge. Honor `.gitignore`, skip dependencies, update incrementally by file fingerprint, and keep why-comments and ADR links attached to the code they explain. Produce a short report (hubs, surprising links, open questions) and answer questions with the relevant subgraph, not whole files.

## Common mistakes

- Searching before knowing which entity you mean.
- Calling a failed source "nothing found".
- A citation that exists but does not say what the sentence claims.
- Scrubbing human voice out of text while keeping the filler.
- A diagram of 25 boxes, or one drawn for a fact that fits in a sentence.
- Presenting inferred links as facts.

## Before you ship

- [ ] Queries, dates and outcomes recorded; failed sources named.
- [ ] Every citation opened and checked against its claim; nothing invented.
- [ ] Prose reread for tells and facts; code and quotes untouched.
- [ ] Diagram within budget, path walked, inferred edges marked.

## Sources

Read 2026-11-25: mvanhorn/last30days-skill (entity plan, tiers, source outcomes), blader/humanizer (pattern list and procedure), Nutlope/hallmark (invented-content and slop floor), cathrynlavery/diagram-design (gate, budget, connector rules), tt-a1i/archify (diagram types, finalize gate), K-Dense-AI/scientific-agent-skills (literature-search and citation rules), Graphify, Understand-Anything and codegraph (method only). Install scripts, hooks, cookie reading and telemetry were not adopted.
