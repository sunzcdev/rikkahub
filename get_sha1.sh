#!/bin/bash

# Display package names
echo "Package Names:"
echo "Release: me.rerere.rikkahub"
echo "Debug: me.rerere.rikkahub.debug"
echo ""

# Get debug SHA1
echo "Debug SHA1 Fingerprint:"
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android 2>/dev/null | grep -A 10 "SHA1:" || echo "Debug keystore not found or error reading it"
echo ""

# Get release SHA1
echo "Release SHA1 Fingerprint:"
keytool -list -v -keystore app.jks -alias sunzc -storepass sunzc123 -keypass sunzc123 2>/dev/null | grep -A 10 "SHA1:" || echo "Release keystore not found or error reading it"
echo ""

echo "Configure these SHA1 fingerprints in your Amap Console:"
echo "Console: https://console.amap.com/"
