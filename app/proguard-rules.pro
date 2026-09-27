# FocusGuard ships with minification disabled by default (see app/build.gradle).
# These rules are here for anyone who turns R8 shrinking/obfuscation back on.
-keep class com.focusguard.blocker.service.BlockerAccessibilityService { *; }
-keep class com.focusguard.blocker.receiver.** { *; }
