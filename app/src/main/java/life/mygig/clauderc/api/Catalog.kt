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
            "Account › Access Tokens › Generate new token (starts with sbp_; project API keys won't work). " +
                "It has the same access as your account (no scopes); " +
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
        ServiceDef(
            "mxroute", "MXroute", "Email hosting: domains, accounts, forwarders via api.mxroute.com",
            listOf(
                FieldDef("API key", minLength = 16),
                FieldDef("Server (e.g. yourserver.mxrouting.net)", secret = false, minLength = 3),
                FieldDef("Username", secret = false, minLength = 1),
            ),
            "https://panel.mxroute.com/api-keys.php", "Create an API key on MXroute",
            "MXroute panel › API Keys › create a key. Copy the key (shown once), your server name (the host in your " +
                "panel address, e.g. yourserver.mxrouting.net) and your panel username.\n" +
                "The app checks them by listing your domains.\n" +
                "Saved as MXROUTE_API_KEY, MXROUTE_SERVER and MXROUTE_USERNAME, which also reach MCP servers that " +
                "use them (like an MXroute MCP server).",
        ),
        ServiceDef(
            "googleplay", "Google Play Console", "Publish Android apps (fastlane, Gradle Play Publisher)",
            listOf(FieldDef("Service account JSON key", multiline = true, minLength = 50)),
            "https://developers.google.com/android-publisher/getting_started", "Open Google's setup guide",
            "1. Google Cloud: pick or create a project and enable the Google Play Android Developer API.\n" +
                "2. Create a service account (IAM & Admin › Service accounts), then Keys › Add key › JSON. No Cloud " +
                "role is needed.\n" +
                "3. Play Console › Users and permissions › Invite new users: enter the service account's email " +
                "(client_email in the key) and grant, per app or for the account:\n" +
                "• Release apps to testing tracks\n" +
                "• Release to production, exclude devices, and use Play App Signing (only if Claude should ship to " +
                "production)\n" +
                "• View app information (read-only)\n" +
                "Invite user. Linking a Cloud project under API access is no longer needed.\n" +
                "4. Choose the downloaded JSON key file here (or paste it). The app checks it with Google (it can't " +
                "check the Play Console invite itself).\n" +
                "One key covers all your apps: in Play Console grant the service account each app (Users and " +
                "permissions › the account › App permissions › Add app).\n\n" +
                "Saved to a private file: GOOGLE_PLAY_JSON_KEY and SUPPLY_JSON_KEY (fastlane) hold its path, " +
                "ANDROID_PUBLISHER_CREDENTIALS (Gradle Play Publisher) its contents.",
        ),
    )

    fun byId(id: String) = services.firstOrNull { it.id == id }

    /** The environment variables each connection gives Claude's sessions (names only). */
    val envVars = mapOf(
        "cloudflare" to listOf("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID"),
        "vercel" to listOf("VERCEL_TOKEN"),
        "netlify" to listOf("NETLIFY_AUTH_TOKEN"),
        "fly" to listOf("FLY_API_TOKEN"),
        "railway" to listOf("RAILWAY_API_TOKEN"),
        "supabase" to listOf("SUPABASE_ACCESS_TOKEN"),
        "neon" to listOf("NEON_API_KEY"),
        "npm" to listOf("NPM_TOKEN"),
        "stripe" to listOf("STRIPE_API_KEY"),
        "huggingface" to listOf("HF_TOKEN"),
        "b2" to listOf("B2_APPLICATION_KEY_ID", "B2_APPLICATION_KEY"),
        "gcp" to listOf("CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE", "CLOUDSDK_CORE_PROJECT"),
        "firebase" to listOf("GOOGLE_APPLICATION_CREDENTIALS"),
        "mxroute" to listOf("MXROUTE_API_KEY", "MXROUTE_SERVER", "MXROUTE_USERNAME"),
        "googleplay" to listOf("GOOGLE_PLAY_JSON_KEY", "SUPPLY_JSON_KEY", "ANDROID_PUBLISHER_CREDENTIALS"),
        "youtube" to listOf("YOUTUBE_CLIENT_ID", "YOUTUBE_CLIENT_SECRET", "YOUTUBE_REFRESH_TOKEN"),
    )
}
