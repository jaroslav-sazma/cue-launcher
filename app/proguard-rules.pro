# R8 runs in full mode. Room, kotlinx.serialization and Ktor ship their own consumer rules, so this
# file carries only what the app itself decides. A missing keep rule shows up as a broken release
# build, never as a broken debug build — which is why CI builds release on every run.

# Verbose, debug and info logs are diagnostics for development and are stripped from release.
# Warnings and errors stay, so a bug report still says what went wrong; none of them carries
# personal data (docs/security-audit.md, "Sensitive data in logs").
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
