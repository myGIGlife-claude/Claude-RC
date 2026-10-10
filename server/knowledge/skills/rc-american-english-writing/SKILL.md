---
name: rc-american-english-writing
kind: process
description: Write, edit and proofread natural American English: emails, complaints, website/app/UI copy, release notes, docs, replies. Use for any prose a person will read, "make this sound natural/human", proofreading, tone, UI strings, AI-sounding or over-formatted text. Not for code.
---
# American English writing and editing (process, not facts)

> Judgment and workflow for prose. Style guides disagree with each other and change slowly: pick the one that governs the task (see `references/sources.md`), mark uncertain usage as uncertain, never invent a rule number or quote. This is guidance in a file, not model training.

## Default standard
Natural, current American English with the judgment of an experienced adult. Ordinary words, accurate specifics, clean sentences, varied rhythm, warmth or firmness to fit the moment. Clear and direct. Plain text for plain prose. Preserve the user's voice when editing. Never invent facts, quotes, experiences, features, prices, laws or guarantees. Do not fake age, memories or a regional accent: the target is fluency and maturity, not a costume.

## Do this

### 1. Settle the job before writing
- Who writes, who reads, what the reader must know or do, how much time they have, the medium, the relationship, the emotion. If one missing fact would change the draft, ask one short question; otherwise choose a sensible default and say so in a line.
- Separate facts that must not change (names, numbers, dates, quotes, legal or technical meaning) from wording you may improve.
- Pick the depth and stay in it: **proofread** (errors only), **light copyedit** (errors, clarity, small trims, voice untouched), **line edit** (sentence-level rework), **developmental edit** (structure, argument, order), **full rewrite**. A request for "fix the grammar" is a proofread: do not restyle the message.

### 2. Edit a user's draft
- Read it whole first. If it already works, say so and change little. A rewrite that is worse than the original is a failure, so be willing to return the original with two fixes.
- Keep their directness, humor, slang level, contractions, fragments, and justified anger. Do not turn a plainspoken person into a press office. Do not soften a legitimate complaint.
- "Sounds like me" means: base it on their pasted samples and stated preferences. Never infer voice from age, region, education or any demographic label.
- Changes the reader could object to (cut content, shifted meaning, changed tone) get a one-line note; everything else is silent. Give a change log only when asked.

### 3. Write the sentence a real American would say
- Everyday verbs over nominalizations ("decide", not "make a determination"). Concrete nouns and numbers over abstractions. Familiar word over inflated one ("use", not "utilize"; "help", not "facilitate").
- Contractions where speech would use them; none in a formal notice or legal letter. Fragments are fine on purpose. Vary length by meaning, not by template; a short sentence only when it earns its place.
- Say it aloud. If you would stumble, or nobody would say it that way, rewrite it. Prefer the idiom a native speaker reaches for, but use an idiom only when it is natural and clear to the reader, never to seem human.
- Confidence matches the facts: no hedging on settled things, no certainty on guesses. Cut qualifiers that carry nothing ("very", "really", "somewhat", "in order to").
- Start with the point. End when the point is made: no recap of what you just said, no offer of more help, no sales line.
- Grammar vs effectiveness: a grammatical sentence can still be bad. Distinguish true errors from style preferences (singular "they", split infinitives, ending on a preposition, "which/that", "who/whom" in speech, sentence-initial "and/but" are all accepted by current usage references). Say "style choice" when it is one.

### 4. Pick the voice for the context
| Context | What it sounds like |
|---|---|
| Text or chat with a friend | Short, loose, contractions, no sign-off |
| Mature friendly note | Plain, warm, specific; one concrete detail beats three adjectives |
| Complaint or dispute | Facts in order (what, when, amounts, who you spoke to), the exact remedy you want, a date. Firm, not theatrical. No threats you cannot carry out |
| Business email | Answer or ask first line; what you need and by when; brief thanks only if sincere |
| Technical explanation | Define terms once, give an example, state limits; match the reader's expertise |
| Instructions | Imperative verbs, one action per step, expected result, what to do if it fails |
| Support reply | Acknowledge the actual problem in your own words, give the fix or the next step, no boilerplate sympathy |
| Marketing / site copy | Say what the thing does and for whom, with a real detail; restraint over superlatives; no claim you cannot support |
| Formal (legal, policy) | Precise, complete sentences, no slang; plain language where the law allows |
| Humor, sarcasm | Light touch; only when the user's voice or the setting calls for it; never explain the joke |
Do not make every voice cheerful, polished and diplomatic. Sometimes brief, skeptical, disappointed or angry-but-controlled is right.

### 5. Formatting: plain unless the deliverable needs more
- Ordinary prose (emails, messages, replies, posts, letters, comments, descriptions) is plain text: no headings, bold labels, bullets, tables, emoji, dividers or code fences, and no Markdown characters in text meant to be pasted somewhere.
- No wrapper commentary ("Here is a polished version") unless the user needs it. No placeholders, "Subject:", greetings or signatures unless the format requires them. Never leave drafting notes or rule language in finished copy.
- Use real structure when the thing calls for it: a README or `.md` file, docs, a report, a comparison table, instructions with real steps, semantic HTML for web pages, platform-native UI text. Keep intentional line breaks and platform formatting.
- Punctuation as a tool, not a tic: em dashes, colons, semicolons, parentheses, exclamation points and quote marks for emphasis are fine in moderation. If a paragraph leans on one, vary it. Do not swap one mechanical habit for another.

### 6. Website, app and UI copy
See `references/ui-copy.md`. Short version: say exactly what the product does; one term per thing everywhere; buttons are verbs that name the result; errors say what happened and what to do, without blame; permission and privacy text states what, why, and what happens if the user says no; never promise what the code does not do; review the whole flow for repeats, contradictions and label drift.

### 7. Practical writing facts (US)
- Dates: "October 10, 2026" in prose; avoid all-numeric dates that could read as D/M. Money: "$1,250.00" or "$1,250". Phone: (555) 010-0100 style. Addresses: street, city, state abbreviation, ZIP. Times: "3:30 p.m." or "3:30 PM", consistent. Units: US customary for US readers, metric for science or when asked.
- Spelling: -ize/-or/-er/-ense (organize, color, center, defense, program, gray, check, traveled). Check uncertain words in a current dictionary rather than guessing.
- Punctuation: periods and commas inside closing quotation marks; serial comma is the default in most US styles but journalism (AP) drops it; follow the governing guide or the user's own habit.
- Word or character limits: count with a tool (`wc -w`, `wc -m`, or code), never by eye.
- Legal, medical, financial, regulated text: edit the language, keep every qualification, do not invent laws, obligations, diagnoses or entitlements, and say plainly that this is editing, not professional advice.

### 8. Dialect and respect
- Default: clear, widely understood modern US English. Regional or community voice only when the user asks or supplies it, and then written as a person would, not as a caricature (no phonetic dialect spelling, no forced folksiness). Keep dialect the user wrote.
- Inclusive, accurate terms; no stereotypes by state, class, race, gender, age or education. Do not flatten a distinct voice into bland standard English.

### 9. Alternatives and tests
- "Give me two versions" means two genuinely different voices or strategies (for example blunt vs. diplomatic), not synonym swaps.
- Max length: write to it, then measure.
- Do not claim text will pass AI detectors; they are unreliable and no style proves human authorship. Aim for originality, specifics and fit.

## Common mistakes
- Opening with filler ("In today's fast-paced world", "I hope this finds you well" when nobody asked), or praise ("great question").
- Inflated words with no evidence: revolutionary, game-changing, seamless, robust, unlock, empower, elevate, leverage, cutting-edge, delve, navigate the complexities, at its core. Replace with the plain fact. A real use of one of these words is allowed.
- The "not just X, it's Y" frame, three-item lists by reflex, slogan rhythm, perfectly even paragraph lengths, a closing paragraph that repeats the opening.
- Restating the point three ways; explaining the obvious; ceremonial transitions ("Moreover", "Furthermore", "It is important to note that").
- Hedging everything ("may potentially help") or overclaiming ("guaranteed").
- Cheerful reassurance in every message, exclamation points as filler, forced chattiness in a business letter.
- Headings and bold for a three-sentence reply; bullets that break a thought into fragments; emoji in a serious message.
- Editing for show: rewriting every sentence, "upgrading" plain words, flattening the writer's personality, or changing facts while "improving" flow.
- Fixing a style preference as if it were an error, or the reverse (letting a real error through because it is "how people talk").
- Copying a style-guide rule you could not check, or quoting a source you did not read.
- Fake regional voice, fake age, fake personal anecdotes.
- Describing UI with filler nouns ("card", "tile", "widget") where a plain sentence says what the user sees or does.

## Before you ship
- [ ] Purpose, audience, medium and depth were clear; facts, names, numbers and quotes unchanged.
- [ ] If editing: original voice, meaning and force preserved; nothing rewritten that already worked.
- [ ] Read aloud once: nothing a person would stumble on or never say.
- [ ] No filler opening, summary ending, stock phrases, or repeated sentence formula; no unsupported superlatives.
- [ ] Formatting matches the destination (plain text for prose, real markup only where needed); no stray Markdown or process language.
- [ ] Grammar, spelling (US), punctuation, capitalization, dates, money and units checked; word or character limit measured.
- [ ] UI copy: one term per thing, verbs on buttons, errors with a next step, no promise beyond what the product does.
- [ ] Nothing invented; anything unverified or a judgment call is said plainly.
