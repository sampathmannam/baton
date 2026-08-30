# KaavalanNote Product Redesign

Date: 2026-08-30  
Status: Approved product design; awaiting written-spec review  
Initial platform: Android  
Future platform: iOS after the Android release is stable

## 1. Product definition

KaavalanNote is a private, local-first instruction and follow-up ledger for one officer. WhatsApp remains the communication channel. KaavalanNote helps the user remember instructions, deadlines, people responsible, and the next follow-up without reading, copying, monitoring, or synchronizing WhatsApp conversations.

The first release is single-user and Android-only. Each installation has independent data and uses the owner's own DeepSeek API key and Google account. There is no shared backend, team account, subordinate tracking, GPS tracking, or automatic collection of replies.

The product succeeds when the user can capture an instruction quickly, see everything requiring attention in one timeline, follow up through WhatsApp, and recover the encrypted data after replacing the phone.

## 2. Product principles

- Simple enough to use throughout a busy working day.
- Reliable without internet for all core record-management functions.
- Private by default, with explicit confirmation before cloud AI receives data or changes a record.
- WhatsApp-aware but never a WhatsApp surveillance or synchronization tool.
- Preserve original evidence; AI-generated summaries never overwrite the original capture.
- Prefer a small dependable feature set over speculative CRM features.
- Keep the record format and service boundaries portable for a later iOS app.

## 3. Scope

### Included

- Timeline dashboard
- People profiles and private WhatsApp-group labels
- Text, Android speech-to-text, photo OCR, document, and Android Share capture
- To do, Waiting, and Done statuses
- Normal and Urgent priority
- Deadline and next-follow-up dates
- Local reminders with snooze
- Searchable archive
- Context-aware WhatsApp drafts
- DeepSeek extraction, summaries, questions, and proposed changes
- Local redaction before DeepSeek calls
- English and Tamil UI and mixed-language AI input
- SQLCipher local encryption
- Encrypted Google Drive backup and restore

### Excluded

- Shared backend or team synchronization
- Subordinate accounts, acknowledgement tracking, or command monitoring
- GPS or continuous location tracking
- WhatsApp chat reading, reply ingestion, group monitoring, delivery receipts, or automatic sending
- Local LLMs, llama.cpp, Qwen models, Whisper, or another local generative model
- Today's Win, Worry Box, meeting brief, quiet-a-while reminders, and relationship tracking
- Polite, urgent, and casual tone categories
- Tags, birthdays, important dates, or general-purpose social CRM features
- Vault mode or a separate app PIN
- Gamification and productivity scoring

Android's built-in speech recognition and on-device ML Kit photo OCR remain. They are capture utilities, not local generative AI.

## 4. Implementation approach

Simplify the existing native Kotlin and Jetpack Compose application. Preserve working capture, OCR, speech, encryption, notification, and test infrastructure where it fits this design. Remove obsolete features and dependencies surgically.

A fresh Android rewrite and a Flutter rewrite were rejected because they discard working components, increase delivery time, and add avoidable platform-integration risk. The Android release comes first. A later iOS project will implement the finalized behavior using the versioned interchange format and the same DeepSeek contracts.

## 5. Information architecture

The app has three primary destinations.

### 5.1 Timeline

Timeline is the opening screen and the complete operational dashboard. It contains one list grouped by the next actionable date:

1. Late
2. Today
3. Next 7 days
4. Later

Filter chips are All, To do, Waiting, and Done. Urgent is a flag shown on a row, not a separate status. Every row makes it clear whether the action is with the user or another person.

A prominent capture action is available from Timeline and People.

### 5.2 People

A person profile contains:

- Name
- Phone number
- Rank or role
- Unit
- Linked active and completed instructions

A WhatsApp group is stored only as a private destination label. A group instruction may optionally identify one responsible person. The app does not attempt to discover WhatsApp groups or open a group directly because Android and WhatsApp do not provide a dependable contract for that behavior.

### 5.3 Ask AI

Ask AI answers questions grounded only in local KaavalanNote records, such as what requires attention today or what is pending with a person. It can summarize records, draft follow-ups, and propose new instructions or changes to status and dates.

Every mutation is presented as a preview and requires explicit confirmation. Ask AI never silently changes data.

### 5.4 Settings

Settings is behind the profile or overflow menu and contains:

- English or Tamil language
- DeepSeek API-key setup and connection test
- Google Drive backup and restore
- Data export
- App and privacy information

## 6. Record model

An instruction contains:

- Stable local identifier
- Original text and capture-source metadata
- Editable action summary
- Optional person
- Optional WhatsApp-group label
- Optional responsible person for a group instruction
- Status: To do, Waiting, or Done
- Priority: Normal or Urgent
- Optional hard deadline
- Optional next-follow-up date and time
- Zero or more locally stored photos or documents
- Created, modified, completed, archived, and deleted timestamps as applicable
- AI-processing state and last non-sensitive error category
- Change history sufficient to explain user- or AI-confirmed edits

`To do` means the next action belongs to the user. `Waiting` means a response or action is expected from someone else. `Done` means no further action is required. Dropping an instruction is an archive action, not another status.

Done records remain in a searchable archive until the user manually deletes them. Deleting a record also deletes its private attachment copies after confirmation.

The app stores both deadline and follow-up because they answer different questions. The deadline is when the work must be complete. Follow-up is when the user should next act. Timeline grouping and reminders use the next actionable date while still displaying an approaching or missed deadline.

## 7. Capture and AI data flow

1. The user captures text, speech, an image, a document, or shared content.
2. The original capture and attachments are committed locally before any network request.
3. A local redaction step replaces detected known names, phone numbers, locations, case references, and other configured identifiers with stable placeholders.
4. The review surface shows the exact redacted content proposed for transmission. The user can continue with DeepSeek or choose manual processing.
5. DeepSeek returns a structured proposal containing action, person or group, status, deadline, follow-up, and priority.
6. The app validates the response and rehydrates placeholders locally.
7. The user confirms or edits one compact proposal card.
8. Only confirmed structured fields are applied to the record. The original capture remains unchanged.

After saving a new capture, the app automatically prepares its locally redacted transmission preview. The DeepSeek request begins only after the user approves that preview. Ask AI, summaries, and follow-up drafts run only when requested. There is no continuous background analysis.

If DeepSeek or connectivity is unavailable, the record remains fully usable and manually editable. A unique queued job retries later with bounded backoff. Retries are idempotent and cannot create duplicate instructions or overwrite later user edits.

Automated redaction is risk reduction, not a guarantee. The UI must say this plainly and always provide a no-cloud path.

## 8. WhatsApp workflow

The user may either compose an outgoing instruction in KaavalanNote before opening WhatsApp or record an instruction that was already sent or received.

For follow-up, DeepSeek produces one context-aware draft. The user may request Shorter, Firmer, Softer, Tamil, or English variations. The app then opens WhatsApp with prepared text where supported. The user chooses the person or group and sends manually.

Opening or returning from WhatsApp must not mark an instruction sent, acknowledged, or done. KaavalanNote records only what the user explicitly confirms.

## 9. Reminders

The app schedules one local notification at the next-follow-up time. Actions are Open, Done, and Snooze. Ignoring a notification does not trigger repeated alerts; the item remains visibly Late.

Snooze offers one hour, tomorrow at 9:00 AM, and a custom date/time. Reminder scheduling is recalculated after relevant edits, application or phone restart, reboot, timezone change, and restore.

## 10. DeepSeek integration

Each installation uses the owner's DeepSeek API key. The key is stored using Android secure storage backed by the Keystore and is excluded from logs, exports, crash reports, and backups.

The integration is isolated behind a provider interface so model names, endpoints, timeouts, and response parsing can change without altering UI or domain logic. Requests use schema-constrained structured responses where supported and strict local validation in all cases. Prompt and response logs must not contain original private content.

The app uses DeepSeek for:

- Capture extraction
- Ask AI queries over selected local context
- Summaries
- Follow-up drafting and refinement
- Proposed record changes

The app does not use DeepSeek for background monitoring or autonomous mutations.

## 11. Storage, security, and backup

- Use one SQLCipher-encrypted local database as the source of truth.
- Protect database and backup key material with Android Keystore facilities.
- Rely on the phone's device lock; do not add a separate app PIN or Vault.
- Store private attachment copies in app-private storage and include them in encrypted backups.
- Create an encrypted Google Drive backup daily when constraints permit.
- Retain the latest 30 successful backups and provide Back up now.
- Exclude the DeepSeek API key and ephemeral authentication tokens.
- Require the user's Google account and recovery secret to restore on another device.
- Include format version, manifest, counts, and integrity checks in every backup.
- Verify upload completion before marking a backup successful.
- Validate and stage a restore before replacing active data.
- Reject corrupted, incompatible, or incorrectly decrypted backups without modifying current data.

The backup and export formats are versioned and platform-neutral. They do not expose plaintext records. A future iOS app must use the same documented logical schema even if its internal database implementation differs.

## 12. Component boundaries

- **Timeline UI:** displays grouped instructions and forwards user intents.
- **People UI:** manages people, private group labels, and linked records.
- **Capture service:** normalizes text, speech, OCR, documents, and shared content.
- **Instruction domain:** owns statuses, dates, priority, transitions, grouping, and archive rules.
- **Local repository:** owns encrypted records, attachments, migrations, and transactions.
- **Redaction service:** creates and rehydrates placeholder mappings locally.
- **AI gateway:** submits redacted requests, validates responses, and emits proposals only.
- **AI job queue:** performs idempotent retry without owning user records.
- **WhatsApp composer:** prepares external intents but records no delivery claim.
- **Reminder scheduler:** schedules and reconciles local notifications.
- **Backup service:** creates, validates, uploads, retains, and restores encrypted archives.

These boundaries should use narrow interfaces so Qwen can modify and test one area without loading or rewriting the entire application.

## 13. Reliability requirements

- Capture, Timeline, People, search, record editing, attachments, and reminders work offline.
- The original capture survives process death, network loss, malformed AI output, and retry.
- AI retries cannot duplicate records or overwrite newer local revisions.
- External-app intents never imply delivery or completion.
- Missing or unreadable attachments show an actionable warning instead of crashing.
- Database migrations preserve all recoverable existing records and map old states deterministically.
- Backup success requires local archive validation and confirmed Drive upload.
- Restore validation occurs before active-data replacement.
- User-facing failures offer a relevant action: Retry, Edit manually, Reconnect, or Keep current data.

## 14. Existing-status migration

The migration from the current seven-status model is deterministic:

- OPEN and CARRIED_OVER become To do.
- ACK_PENDING, IN_PROGRESS, and WAITING_ON_OTHER become Waiting only when their existing ownership metadata indicates the next action is external; otherwise they become To do.
- DONE becomes Done.
- DROPPED becomes archived while preserving its previous terminal state in migration metadata.

Ambiguous records must be placed in To do and shown once in a migration-review list. Migration never deletes original notes or attachments.

## 15. Testing and acceptance

Qwen must add or update automated tests for:

- Status transitions and legacy migration
- Timeline grouping across locale, timezone, daylight, and overdue boundaries
- Deadline versus follow-up behavior
- Redaction and placeholder rehydration
- Malformed, delayed, duplicate, and failed DeepSeek responses
- Retry idempotency and protection from stale AI results
- WhatsApp intent behavior without delivery-state mutation
- Reminder scheduling, snooze, reboot, and timezone reconciliation
- Encrypted database access and migrations
- Backup integrity, retention, wrong-secret handling, interrupted upload, and safe restore
- Attachment lifecycle and missing-file behavior
- English, Tamil, and mixed-language capture
- Accessibility and essential Compose UI flows

Before release, test on at least one physical Android phone using realistic offline, reboot, notification, WhatsApp, Drive OAuth, backup, restore, OCR, speech, and Tamil scenarios. A release is not accepted merely because unit tests pass.

## 16. Distribution

Development builds may be installed directly on the owner's Android phone. Stable builds should move through controlled Google Play testing and then a controlled production release to provide dependable updates.

The later iOS project will use TestFlight during development and may use unlisted App Store distribution for the finished app. iOS is not part of this implementation plan.

## 17. Qwen-led delivery process

Qwen performs implementation, refactoring, test writing, documentation updates, and routine debugging in staged work packages:

1. Establish a reproducible baseline build and test report.
2. Introduce the simplified domain model and safe migration.
3. Replace navigation and Timeline.
4. Simplify People and private group labels.
5. Complete capture, attachment, archive, and reminder flows.
6. Add redaction, DeepSeek proposals, Ask AI, and drafting.
7. Complete encrypted Drive backup and restore.
8. Remove excluded features, local-model code, unused dependencies, and stale documentation.
9. Run automated and physical-device acceptance checks and produce a signed release candidate.

Each stage must define changed files, tests, observed results, unresolved risks, and the next stage. Qwen must not perform an unreviewed whole-project rewrite.

Codex involvement is deliberately limited to preparing staged prompts, reviewing milestone evidence, and intervening for unresolved build failures, device access, OAuth, signing, Play Console work, or security-critical issues.

## 18. Final acceptance criteria

The Android redesign is complete when:

- A user can capture and recover an instruction without internet or AI.
- Timeline accurately shows Late, Today, Next 7 days, and Later.
- To do, Waiting, Done, Normal, and Urgent behave exactly as specified.
- DeepSeek receives only user-approved redacted content and never mutates records without confirmation.
- WhatsApp drafting works without claiming delivery or reading replies.
- Reminders survive normal device lifecycle events and remain non-repetitive.
- Encrypted backup and restore succeed on a physical device with the API key excluded.
- Existing records migrate without silent loss.
- English and Tamil core journeys pass automated and physical-device testing.
- Removed features and local generative-model dependencies are absent from UI, runtime, and documentation.
