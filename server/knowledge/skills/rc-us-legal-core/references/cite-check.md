# Cite-checking without a paid citator (checked 2026-10-10)

## Authority record (one per material authority, kept in the matter's authority log)
```
ID:            A-07
Authority:     <case name | statute section | rule | regulation>
Cite:          <official cite + pin (page or Ohio paragraph number)>
Court/agency:  <court, district/division>       Jurisdiction: <state / federal circuit>
Date:          <decided / enacted>              In force for our date (YYYY-MM-DD)? yes / no / unclear (why)
Version:       <effective window of the text used>
Source:        <official URL or retrieval ID>   Verified on: YYYY-MM-DD   By: <session/person>
Citator:       none (free check: CourtListener cited-by, Google Scholar How cited, docket search) / <paid tool>
Proposition:   <one sentence, exactly what we cite it for>
Holding/dicta: holding | dicta | concurrence | dissent | statute text | rule text
Status:        binding here | persuasive | unpublished (rule: FRAP 32.1 / Rep.Op.R. 3.4 / N.C. R. App. P. 30(e) / other)
Quote checked: yes (word for word) / no quote
Limits:        exceptions, distinguishing facts, contrary authority (IDs)
Later history: none found as of <date> | appealed / reversed / vacated / overruled / superseded by <cite>
Notes:
```

## Verification steps, in order
1. Existence: retrieve the full text from an official site or a free finder. A citation that resolves to nothing is not authority.
2. Identity: case name, court, date, docket number and reporter cite all match. Wrong-court or wrong-year cites are common errors.
3. Text: read the passage around the pin cite. Is it the court's reasoning, a quoted party argument, a recited lower-court view, or dicta?
4. Quote: compare character by character, including ellipses and brackets. Pin cite the exact page or paragraph.
5. Later history: search the case name and cite for later decisions in the same case (appeal, remand, rehearing en banc, vacatur) and for
   cases that overrule or question it; check whether a statute or rule amendment superseded it; for statutes and rules, check the
   amendment history up to today.
6. Weight: binding only if from a court whose decisions bind THIS court on THIS question (federal law vs state law matters). Note splits.
7. Unpublished: check the citing court's rule. Federal appellate: FRAP 32.1 bars restricting citation of unpublished federal opinions
   issued on or after 2007-01-01; attach a copy when not in a public database. Circuit rules still decide precedential weight.
   Ohio: Rep.Op.R. 3.4 (after 2002-05-01, cite regardless of publication). NC: N.C. R. App. P. 30(e)(3) (not controlling; citation
   disfavored except preclusion or law of the case; otherwise only with a served copy when no published opinion serves as well; text
   read in the 2023-03-01 official codification on 2026-10-10, re-check the current codification).
8. Record it and write "No citator available; existence, text and later history checked on <sources> on <date>" when that is the truth.

## Free tools and their limits
- CourtListener citation lookup API (v4): give it a citation or a block of text; it returns found (200), not found (404), unknown
  reporter (400), ambiguous (300). Needs a free account token; 60 valid citations per minute, 250 per request, 64,000 characters per text.
  It only matches citations with volume and page; it does not check statutes, id./supra, or whether a case supports a proposition.
  A 404 means "not in CourtListener", not proof of fabrication: try Google Scholar and the court site before calling it nonexistent.
- Google Scholar "How cited" and CourtListener "cited by" list later citing cases; read the ones that discuss the case, not all of them.
- When the stakes are high and no citator is available, say a lawyer or law librarian with Shepard's/KeyCite should run the final check.

## Fabricated or doubtful citations
Signals: reporter/volume/page that do not exist or mismatch the year; a court that did not exist then; a case name that returns
nothing; a perfect quote on exactly the needed point that no other case repeats; a pin cite past the opinion's last page.
Response: (1) attempt verification as above; (2) if not found, say so plainly and drop it; (3) never paraphrase its supposed holding;
(4) if a similar real case exists, present it as a different case with its own record; (5) in a response brief, note neutrally that
the cited authority could not be located. Courts sanction lawyers and self-represented parties for filing fictitious citations
(the signer certifies legal contentions under FRCP 11(b)(2) or the state analog), so this check is never optional.

## Second-pass audit (expanded notes on the 18 questions in SKILL.md section 6)
- Q2/Q5: re-pull statute and rule text for the relevant date AND today; amendments between the two are the usual trap.
- Q4: if the case only supports a narrower point, narrow the sentence; do not stretch the case.
- Q6: search for adverse binding authority on purpose (search the opposite proposition). Disclosing controlling adverse authority is an
  ethics duty for lawyers in most states (ABA Model Rule 3.3(a)(2): "legal authority in the controlling jurisdiction known to the lawyer
  to be directly adverse" and not disclosed by opposing counsel; wording confirmed 2026-10-10 in state adoptions (CO, IN, MN), the ABA
  page itself was not machine-readable; state versions vary, e.g. Georgia numbers it (a)(3)).
- Q9/Q10: the procedural vehicle decides available relief (e.g. a void-judgment challenge vs a discretionary relief-from-judgment
  motion have different time limits and standards in many courts; see rc-us-litigation-procedure).
- Q16: frivolous-filing exposure applies to self-represented filers too.
- Q18: write the opponent's best three points; fix or disclose each.

## Lawyer AI-ethics context
ABA Formal Opinion 512, "Generative Artificial Intelligence Tools" (2024-07-29; title and date read from the opinion's own header on
2026-10-10) covers competence, confidentiality, client communication, meritorious claims and candor, supervision, and fees: understand the
tool's limits, verify output before relying on it, review the tool's terms before entering client data, get the client's informed consent
where use risks disclosure (boilerplate engagement-letter consent is not enough), and do not bill for learning the tool. Body details are
from secondary summaries (the PDF text was not extractable); read the opinion at americanbar.org before citing it.
Many courts have local rules or standing orders on AI-assisted filings (disclosure or certification); check the specific court.
