package life.mygig.clauderc.api

/** One input on a token service's connect form. [multiline] is for pasted JSON keys. */
data class FieldDef(val label: String, val secret: Boolean = true, val multiline: Boolean = false, val minLength: Int = 8)

/**
 * A service connected with a token saved on the server (claude-setup.sh
 * `login-token <id>`). [install] is the `install-cli` name for its CLI, if the
 * server can install it. Texts come from each provider's docs (checked 2026-09).
 */
data class ServiceDef(
    val id: String,
    val name: String,
    val hint: String,
    val fields: List<FieldDef>,
    val createUrl: String,
    val createLabel: String,
    val needs: String,
    val install: String? = null,
)

object Catalog {
    val services = listOf(
        ServiceDef(
            "cloudflare", "Cloudflare", "Workers, R2, KV, D1, Pages via wrangler",
            listOf(FieldDef("API token", minLength = 30), FieldDef("Account ID (account tokens only)", secret = false, minLength = 0)),
            "https://dash.cloudflare.com/profile/api-tokens", "Create token on Cloudflare",
            "Either kind of token works:\n" +
                "• User token: My Profile › API Tokens › Create Token. Leave Account ID empty.\n" +
                "• Account token (cfat_…): Manage Account › Account API tokens › Create Token. Paste the Account ID " +
                "shown with it too (wrangler needs it).\n\n" +
                "Easiest: the \"Edit Cloudflare Workers\" template. It contains:\n" +
                "Account: Workers Scripts: Edit · Workers KV Storage: Edit · Workers R2 Storage: Edit · " +
                "Workers Tail: Read · Account Settings: Read\n" +
                "Zone: Workers Routes: Edit\n" +
                "User: User Details: Read · Memberships: Read (user tokens only)\n\n" +
                "Add if Claude uses them (Account): D1: Edit, Cloudflare Pages: Edit. Then choose the account and " +
                "zone resources, Continue to summary › Create Token.\n\n" +
                "Saved as CLOUDFLARE_API_TOKEN (and CLOUDFLARE_ACCOUNT_ID).",
        ),
        ServiceDef(
            "vercel", "Vercel", "Deploy with the vercel CLI",
            listOf(FieldDef("Token")),
            "https://vercel.com/account/tokens", "Create token on Vercel",
            "Personal account (not a team) › Settings › Account Tokens › Create.\n" +
                "• Scope: Full Account, or your team › All Projects\n" +
                "• Expiration: your choice\n" +
                "The value is shown once. Saved as VERCEL_TOKEN.",
            install = "vercel",
        ),
        ServiceDef(
            "netlify", "Netlify", "Deploy with the netlify CLI",
            listOf(FieldDef("Personal access token")),
            "https://app.netlify.com/user/applications#personal-access-tokens", "Create token on Netlify",
            "User settings › Applications › Personal access tokens › New access token.\n" +
                "• No scopes: it has your full account access\n" +
                "• Tick \"Allow access to my SAML-based Netlify team\" only if you need it\n" +
                "• Pick an Expiration › Generate token\n" +
                "Resetting your password revokes it. Saved as NETLIFY_AUTH_TOKEN.",
            install = "netlify",
        ),
        ServiceDef(
            "fly", "Fly.io", "Deploy with flyctl",
            listOf(FieldDef("Token (starts with FlyV1)", minLength = 20)),
            "https://fly.io/dashboard", "Open the Fly.io dashboard",
            "Create it where you're logged in to Fly:\n" +
                "• fly tokens create org  (can create and manage apps)\n" +
                "• fly tokens create deploy -a <app>  (one app only)\n" +
                "or in the dashboard: your organization (or app) › Tokens.\n" +
                "Paste the whole value including \"FlyV1 \". Fly checks it on first use. Saved as FLY_API_TOKEN.",
            install = "flyctl",
        ),
        ServiceDef(
            "railway", "Railway", "Deploy with the railway CLI",
            listOf(FieldDef("Account token")),
            "https://railway.com/account/tokens", "Create token on Railway",
            "Account settings › Tokens. In the workspace dropdown choose \"No workspace\" for an account token " +
                "(all workspaces). Project tokens (RAILWAY_TOKEN) aren't supported here.\nSaved as RAILWAY_API_TOKEN.",
            install = "railway",
        ),
        ServiceDef(
            "supabase", "Supabase", "Projects, migrations, functions via the supabase CLI",
            listOf(FieldDef("Access token")),
            "https://supabase.com/dashboard/account/tokens", "Create token on Supabase",
            "Account › Access Tokens › Generate new token. It has the same access as your account (no scopes); " +
                "set an expiry.\nSaved as SUPABASE_ACCESS_TOKEN.",
            install = "supabase",
        ),
        ServiceDef(
            "neon", "Neon", "Serverless Postgres via the neon CLI",
            listOf(FieldDef("API key")),
            "https://console.neon.tech", "Open the Neon console",
            "Profile menu › Settings › API keys › Create. A Personal key covers every project you can reach " +
                "(Organization and project-scoped keys also work). Keys don't expire until revoked; the secret shows " +
                "once.\nSaved as NEON_API_KEY.",
            install = "neon",
        ),
        ServiceDef(
            "npm", "npm", "Publish packages to npmjs.com",
            listOf(FieldDef("Access token (npm_…)")),
            "https://www.npmjs.com", "Open npmjs.com",
            "Profile picture › Access Tokens › Generate New Token (granular; the only kind now).\n" +
                "• Packages and scopes: Read and write\n" +
                "• Tick \"Bypass two-factor authentication\" to publish without a code\n" +
                "• Write tokens last at most 90 days\n" +
                "Heads-up: from January 2027 npm tokens can only stage a publish for you to approve on npmjs.com.\n" +
                "Saved as NPM_TOKEN and wired up in ~/.npmrc. npm itself comes with Node.js.",
        ),
        ServiceDef(
            "stripe", "Stripe", "Payments API and the stripe CLI",
            listOf(FieldDef("Restricted or secret key", minLength = 10)),
            "https://dashboard.stripe.com/test/apikeys", "Open Stripe API keys (sandbox)",
            "Start in a sandbox: API keys › Create restricted key.\n" +
                "• Give each resource None, Read or Write as Claude needs (Write includes Read)\n" +
                "• The app checks the key by reading your balance, so allow Balance: Read\n" +
                "• You get rk_test_…; switch to live mode for rk_live_… only when ready\n" +
                "Secret keys (sk_…) work too, but Stripe recommends restricted keys.\nSaved as STRIPE_API_KEY.",
            install = "stripe",
        ),
        ServiceDef(
            "huggingface", "Hugging Face", "Models and datasets via the hf CLI",
            listOf(FieldDef("Token (hf_…)")),
            "https://huggingface.co/settings/tokens", "Create token on Hugging Face",
            "Settings › Access Tokens › New token.\n" +
                "• Fine-grained (Hugging Face recommends it), pick the repos and permissions\n" +
                "• or Read to only download, Write to push\n" +
                "Some organizations require fine-grained tokens or admin approval.\nSaved as HF_TOKEN.",
            install = "hf",
        ),
        ServiceDef(
            "b2", "Backblaze B2", "Cloud storage via the b2 CLI (and rclone)",
            listOf(FieldDef("keyID", secret = false), FieldDef("applicationKey")),
            "https://secure.backblaze.com/app_keys.htm", "Open B2 application keys",
            "B2 Cloud Storage › Application Keys › Add a New Application Key.\n" +
                "• Bucket access: one bucket (or All)\n" +
                "• Type of access: Read and Write\n" +
                "• Tick \"Allow List All Bucket Names\" (the CLI needs to list buckets)\n" +
                "The applicationKey shows only once.\nSaved as B2_APPLICATION_KEY_ID and B2_APPLICATION_KEY.",
            install = "b2",
        ),
        ServiceDef(
            "gcp", "Google Cloud", "gcloud and Cloud Storage with a service account",
            listOf(FieldDef("Service account JSON key", multiline = true, minLength = 50)),
            "https://console.cloud.google.com/iam-admin/serviceaccounts", "Open service accounts",
            "IAM & Admin › Service accounts › create one (or pick one) › Keys › Add key › Create new key › JSON › " +
                "Create. Paste the whole file.\n" +
                "• Give it only what Claude needs, e.g. Storage Object User (roles/storage.objectUser) on a bucket, " +
                "plus Storage Bucket Viewer to list buckets\n" +
                "• Some organizations block key creation (iam.disableServiceAccountKeyCreation)\n" +
                "Saved to a private file; gcloud uses it via CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE, with the key's " +
                "project as the default.",
            install = "gcloud",
        ),
        ServiceDef(
            "firebase", "Firebase", "Deploy with firebase-tools",
            listOf(FieldDef("Service account JSON key", multiline = true, minLength = 50)),
            "https://console.firebase.google.com", "Open the Firebase console",
            "Project settings (gear) › Service accounts › Generate new private key › Generate key. Paste the whole " +
                "file.\n" +
                "• For deploys the account needs Firebase Admin, plus API Keys Viewer for Hosting\n" +
                "Saved to a private file as GOOGLE_APPLICATION_CREDENTIALS (Google's recommended way; FIREBASE_TOKEN " +
                "is deprecated).",
            install = "firebase",
        ),
    )

    fun byId(id: String) = services.firstOrNull { it.id == id }
}
