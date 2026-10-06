#!/usr/bin/env bash
set -euo pipefail

CLASSIFIER="app/src/main/java/com/mesterumailer/smsmanager/util/SmsClassifier.kt"
MAIN_ACTIVITY="app/src/main/java/com/mesterumailer/smsmanager/MainActivity.kt"
RECEIVER="app/src/main/java/com/mesterumailer/smsmanager/SmsReceiver.kt"

test -f "$CLASSIFIER"
test -f "$MAIN_ACTIVITY"
test -f "$RECEIVER"

grep -F 'if (rule.senderContains.isNotEmpty() && senderHits == 0) return 0' "$CLASSIFIER" >/dev/null
if grep -F 'hasReliableTextSender' "$CLASSIFIER" >/dev/null; then
  echo "Obsolete numeric-sender fallback detected in SmsClassifier.kt"
  exit 1
fi

grep -F 'val otp = extractOtp(normalizedBody)' "$CLASSIFIER" >/dev/null
grep -F 'private val otpContextRegex' "$CLASSIFIER" >/dev/null
grep -F 'compareByDescending<Pair<FilterRule, Int>> { it.first.priority }' "$CLASSIFIER" >/dev/null
grep -F 'senderContains = emptyList()' "app/src/main/java/com/mesterumailer/smsmanager/data/FilterRuleRepository.kt" >/dev/null
grep -F 'if (message.analysis.categoryId == SmsCategory.OTP.id)' "$MAIN_ACTIVITY" >/dev/null
grep -F 'analysis.otpCode.takeIf { categoryId == SmsCategory.OTP.id }' "$RECEIVER" >/dev/null

echo "Critical classification/OTP invariants: PASS"
