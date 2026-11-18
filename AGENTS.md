# Android development account

This Windows workspace has repeatedly lost emulator authorization because an ADB server was started by CodexSandboxOffline instead of the Windows account running Android Studio. Different accounts also produced incompatible debug APK signatures.

- Before any adb command (including devices, which can start the server), emulator launch, Gradle connected-device task, or debug APK packaging, verify the effective Windows account. Android Studio currently runs as Administrator.
- Do not perform these operations as CodexSandboxOffline. For user-authorized work, use host execution under the Android Studio account with normal tool approvals. If unavailable, provide the command for the user to run in their Windows PowerShell.
- Project scripts call scripts/assert-android-account.ps1 before device operations or debug APK builds. Server-only builds do not package Android apps and remain usable without this Android account restriction.
- Repair a wrong-account ADB server with scripts/repair-adb.ps1 from the Android Studio account. Preserve existing ADB keys, device authorization and local app data. Do not copy private keys between accounts, bypass authorization or uninstall apps to resolve the mismatch.
- Preserve the existing debug signing identity. Save PowerShell scripts containing Chinese as UTF-8 with BOM for Windows PowerShell 5.1.
