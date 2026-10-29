package life.mygig.clauderc.api

/**
 * "Connect with a password": the one bash script the app sends on stdin of a password SSH login
 * (`bash -s`), so no secret is ever on a command line. It installs missing tools (apt, with sudo),
 * Claude Code and the cLaudeRC scripts, and authorizes this phone's key; after that the key is the
 * only login the app uses. Lines starting with [MARK] tell the app how each step went.
 */
object ServerSetup {
    const val MARK = "@@clauderc "
    const val COMMAND = "bash -s"
    /** The whole run; every step in the script has its own, shorter limit. */
    const val TIMEOUT_MS = 20 * 60_000L
    const val MAX_PASSWORD_TRIES = 3

    /** Same rule as the server's install-launcher-key.sh accepts from the app: an ed25519 key with a plain comment. */
    private val PUBLIC_KEY_RE = Regex("^ssh-ed25519 [A-Za-z0-9+/]+={0,2}( [A-Za-z0-9@._-]{1,64})?$")
    private val USER_RE = HOST_USER_RE

    /** A word for a POSIX shell: single quotes, each ' written as '\''. Safe for any text. */
    fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Null when the fields are fine, else what to fix. Same rules as the server's host actions. */
    fun validate(host: String, port: String, user: String): String? = when {
        host.isBlank() -> "Enter the server's address."
        !hostAddressOk(host) -> "That address has characters a host name can't have."
        port.toIntOrNull()?.takeIf { it in 1..65535 } == null -> "The port is a number from 1 to 65535."
        user.isBlank() -> "Enter the user name."
        !USER_RE.matches(user) -> "A user name is lowercase letters, digits, - and _ (starting with a letter or _)."
        else -> null
    }

    /** The script; [sudoPassword] empty = none given (root and passwordless sudo still work). */
    fun script(phonePublicKey: String, sudoPassword: String): String {
        require(PUBLIC_KEY_RE.matches(phonePublicKey)) { "Unexpected phone key format" }
        require('\n' !in sudoPassword && '\r' !in sudoPassword) { "A password is one line" }
        return "SUDO_PW=" + shQuote(sudoPassword) + "\n" +
            "INSTALL=" + shQuote(Updates.installCommand(phonePublicKey)) + "\n" +
            BODY
    }

    // Multi-dollar string: a single $ is the shell's. main runs with stdin from /dev/null, so nothing it starts can
    // read the rest of this script (bash -s reads it from the same stdin); the last line is the last thing bash reads.
    private val BODY = $$"""
set -u
umask 077
export PATH="$HOME/.local/bin:$PATH"
say() { printf '@@clauderc %s\n' "$*"; }
T=""; command -v timeout >/dev/null 2>&1 && T=timeout
have_all() { missing=""; for c in curl git jq tmux flock; do command -v "$c" >/dev/null 2>&1 || missing="$missing $c"; done; }
as_root() {
  case "$sudo_ok" in
    root) "$@" ;;
    nopasswd) sudo -n "$@" ;;
    ok) sudo -A "$@" ;;
    *) return 1 ;;
  esac
}
main() {
  # tmp is global: the EXIT trap runs after main has returned.
  local sudo_ok=no missing="" pkgs c can
  tmp="$(mktemp -d "$HOME/.clauderc-setup.XXXXXX" 2>/dev/null || mktemp -d)" || { echo "Couldn't make a temporary folder."; SUDO_PW=""; say "install=failed"; return 1; }
  trap 'rm -rf "$tmp"' EXIT
  trap 'exit 130' HUP INT TERM
  chmod 700 "$tmp"

  # sudo: root needs none; passwordless sudo needs no password; else the password goes to a private file that an
  # askpass helper reads (never an argument, never the environment). The first check reads it once only, so a
  # wrong password counts as one failed try, not three.
  if [ "$(id -u)" = 0 ]; then sudo_ok=root
  elif ! command -v sudo >/dev/null 2>&1; then sudo_ok=missing
  elif sudo -n true >/dev/null 2>&1; then sudo_ok=nopasswd
  elif [ -n "$SUDO_PW" ]; then
    printf '%s\n' "$SUDO_PW" >"$tmp/pw"
    printf '%s\n' "$SUDO_PW" >"$tmp/pw1"
    chmod 600 "$tmp/pw" "$tmp/pw1"
    printf '#!/bin/sh\ncat %q\n' "$tmp/pw" >"$tmp/askpass"
    printf '#!/bin/sh\ncat %q && rm -f %q\n' "$tmp/pw1" "$tmp/pw1" >"$tmp/askpass1"
    chmod 700 "$tmp/askpass" "$tmp/askpass1"
    if SUDO_ASKPASS="$tmp/askpass1" sudo -A true >/dev/null 2>&1; then
      sudo_ok=ok
      export SUDO_ASKPASS="$tmp/askpass"
    else
      sudo_ok=denied
      rm -f "$tmp/pw"
    fi
  fi
  SUDO_PW=""
  say "sudo=$sudo_ok"

  have_all
  if [ -n "$missing" ]; then
    case "$sudo_ok" in root|nopasswd|ok) can=1 ;; *) can=0 ;; esac
    if [ "$can" = 1 ] && command -v apt-get >/dev/null 2>&1; then
      pkgs=""; for c in $missing; do [ "$c" = flock ] && c=util-linux; pkgs="$pkgs $c"; done
      echo "Installing$pkgs (apt-get)..."
      as_root env DEBIAN_FRONTEND=noninteractive ${T:+timeout 180} apt-get update -qq </dev/null || echo "apt-get update failed."
      # $pkgs unquoted on purpose: one word per package.
      as_root env DEBIAN_FRONTEND=noninteractive ${T:+timeout 300} apt-get install -y -qq $pkgs </dev/null || echo "apt-get install failed."
      have_all
    elif [ "$can" = 1 ]; then
      echo "No apt-get here: install$missing yourself."
    else
      echo "Missing$missing, and there is no sudo to install them."
    fi
  fi
  say "missing=$(echo $missing | tr ' ' ',')"
  command -v curl >/dev/null 2>&1 || { echo "curl is needed to download the scripts."; say "install=failed"; return 1; }

  # Claude Code first, so the installer can register the Team MCP server with it. Failing here is fine.
  if command -v claude >/dev/null 2>&1 || [ -x "$HOME/.local/bin/claude" ]; then
    say "claude=present"
  else
    echo "Installing Claude Code..."
    if ${T:+timeout 300} bash -o pipefail -c 'curl -fsSL https://claude.ai/install.sh | bash' </dev/null && { command -v claude >/dev/null 2>&1 || [ -x "$HOME/.local/bin/claude" ]; }; then
      say "claude=installed"
    else
      say "claude=failed"
    fi
  fi

  echo "Installing the cLaudeRC server scripts..."
  if ${T:+timeout 300} bash -o pipefail -c "$INSTALL" </dev/null; then say "install=ok"; else say "install=failed"; return 1; fi
  if command -v systemctl >/dev/null 2>&1 && systemctl is-enabled claude-sessions.service >/dev/null 2>&1; then
    say "autostart=ok"
  else
    say "autostart=skipped"
  fi
  return 0
}
main </dev/null; exit $?
"""

    /** How the run went, from the [MARK] lines. */
    data class Report(
        /** root, nopasswd, ok, denied, no (no sudo password given), missing (no sudo), or null (never got that far). */
        val sudo: String? = null,
        val missing: List<String> = emptyList(),
        val installed: Boolean = false,
        /** present, installed, failed or null. */
        val claude: String? = null,
        val autostart: Boolean = false,
    ) {
        val sudoWorked get() = sudo == "root" || sudo == "nopasswd" || sudo == "ok"
    }

    fun parse(output: String): Report {
        var r = Report()
        output.lineSequence().filter { it.startsWith(MARK) }.forEach { line ->
            val (k, v) = line.removePrefix(MARK).trim().split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            r = when (k) {
                "sudo" -> r.copy(sudo = v)
                "missing" -> r.copy(missing = v.split(',').filter { it.isNotBlank() })
                "install" -> r.copy(installed = v == "ok")
                "claude" -> r.copy(claude = v)
                "autostart" -> r.copy(autostart = v == "ok")
                else -> r
            }
        }
        return r
    }

    /** The output as the user sees it: without the [MARK] lines. */
    fun visible(output: String): String =
        output.lineSequence().filterNot { it.startsWith(MARK) }.joinToString("\n")

    /** Plain-language notes for the result screen. [claudeSignedIn] null = the status check didn't run. */
    fun notes(r: Report, claudeSignedIn: Boolean?): List<String> = buildList {
        when (r.sudo) {
            "denied" -> add("The sudo password wasn't accepted, so package installs and boot-restore were skipped.")
            "no", "missing" -> add("Without sudo, package installs and boot-restore (claude-autostart) were skipped.")
        }
        if (r.missing.isNotEmpty()) add("Still missing on the server: ${r.missing.joinToString(", ")}.")
        if (r.installed && !r.autostart && r.sudoWorked) add("Boot-restore (claude-autostart) isn't on yet: run 'claude-autostart install' on the server.")
        when {
            r.claude == "failed" -> add("Claude Code not installed: sign in/installation can be done later.")
            claudeSignedIn == false -> add("Claude is not signed in yet: open Connections > Logins.")
        }
        add("Tip: the app now logs in with this phone's key only. You can turn off password login for SSH on the server if nothing else needs it.")
    }
}
