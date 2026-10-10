# Matter folder: layout, index, and handling rules

A matter folder lets later sessions pick up the work without re-asking. Keep it in the project the user chose (never a public repo;
add it to `.gitignore` if the project is a repo that is pushed anywhere). Plain Markdown and CSV so it opens on a phone.

```
matters/<short-matter-id>/          # id without names, e.g. 2026-oh-support-01
  INDEX.md                          # start here (format below)
  chronology.md                     # date | event | source document | verified/alleged
  docket.md                         # docket entries and orders: date | entry # | title | file in originals/ | read? 
  authorities.md                    # authority log: one record per authority (cite-check.md template)
  deadlines.md                      # deadline records + checklist table (deadlines.md template)
  evidence.md                       # inventory + evidence map
  questions.md                      # open questions, assumptions, things the user must get or confirm
  originals/                        # documents exactly as received; never edited (read-only copies)
  working/                          # extracted text, redacted copies, notes
  drafts/                           # <doc>-v01.md, -v02.md ... + CHANGELOG lines in INDEX.md
```

## INDEX.md format
```
# Matter <id>           Last updated: YYYY-MM-DD
Jurisdiction / tribunal: <court, division, county>      Case no.: [verify] or <from document>
Parties and roles: <roles, not full names where avoidable; minors by initials>
Posture (verified): <what is filed/decided, with docket cites>
Goal: <what the user wants>
Law in force at relevant date: <summary + authority IDs>     Current law for requested action: <summary + IDs>
Deadlines: <next 3, with labels>        Risks / adverse authority: <IDs>
Working assumptions (not verified): ...
Drafts: <file, version, date, status: working | ready for user review | user says filed on <date> (proof: <file>)>
Sources unavailable / to obtain: ...
```

## Evidence map
| Element or issue | Supporting evidence (file) | Missing | Contradictions | Status (verified / unverified) |
|---|---|---|---|---|

## Handling rules
- Preserve originals; extract facts from copies; record where every fact came from.
- Keep verified documents and working assumptions visibly apart in every file.
- Filing receipts, service proof and signed orders go in `originals/` only when the user actually provides them; never create them.
- Sensitive data: store only what the work needs; redact in `working/` copies; minors by initials; partial account and ID numbers.
- No uploads of matter files to third-party services, OCR sites, translation tools or APIs without the owner's explicit go-ahead and a
  note of that service's retention and training terms. AI output and these files are not privileged.
- Update INDEX.md "Last updated" and the open questions at the end of each work session.
