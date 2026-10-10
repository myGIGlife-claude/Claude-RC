# UI, website and product copy (practical rules)

Platform text rules differ: check the platform's own current guidance before applying (see `sources.md`; Apple and Material pages could not be read when this skill was written, so treat the points below as common practice, not citations).

## Labels and actions
- Button = verb that names the result: "Save changes", "Delete 3 photos", "Send invoice". Avoid bare "OK", "Submit", "Yes/No" when a specific verb fits. Destructive choices name what is lost; the safe choice is easy to find.
- Don't say "click" or "tap" in labels or help unless teaching the gesture; say what to do ("Choose a file").
- One name per thing across every screen, notification, email and store listing. Pick it once, keep a glossary in the repo.
- Sentence case for most UI text unless the platform convention or an existing app says otherwise: match what is already there.
- Field labels say what goes in; placeholders are not labels (they vanish and fail accessibility). Helper text states the format or why you ask.
- Short enough to fit a phone at large font sizes. Never truncate the part that carries the meaning.

## Messages
- Error = what happened, why if it helps, what to do next. No blame ("You entered an invalid..."), no raw codes in the main line, no jokes when data was lost.
- Empty state = what belongs here and the one action to fill it. Loading = say what is loading only if it takes long. Success = brief, factual, no exclamation point.
- Confirmation dialog: title states the action as a question or fact, body states the consequence, buttons repeat the verbs.
- Permission request: ask at the moment of use. Say what the app will do, why it needs this, and what still works if the user declines. Never pressure.
- Privacy text: plain facts (what is collected, where it goes, how to delete it). Do not claim "we never..." unless the code proves it.
- Notifications: lead with what changed, not the app name; no marketing in transactional ones.

## Money and legal-adjacent text
- Price, billing period, renewal, trial end and cancellation steps are stated where the user decides, in the same words as the checkout and the receipt. Never invent a refund, guarantee, discount or legal right. Have the owner confirm any text with legal effect.

## Marketing and store copy
- Lead with what the product does for a named kind of person, then one concrete detail (a number, a real feature). Release notes: what changed for the user, in the order they will care, no internal jargon.
- No claim you cannot show: "fast", "secure", "private" need a fact next to them or go.

## Accessibility
- Every icon-only control has a text label for screen readers; link text makes sense out of context; don't convey meaning by color alone; use plain words and short sentences for anything a user must act on.

## Review the whole flow
Read the screens in order. Look for the same idea said twice, a label that changes name between screens, a promise in the headline the next screen contradicts, and copy that is correct but doesn't fit the control it sits on.
