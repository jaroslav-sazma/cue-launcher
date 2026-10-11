#!/usr/bin/env bash
# Auto-approves a ./gradlew call made only of task names and harmless flags. Anything else gets no
# decision here and falls through to the normal permission prompt: other flags (-I, -D, -p, …) can
# load code from outside the project, and chained commands could hide anything. Tasks that install,
# uninstall or run on a device always prompt, because they reach every connected phone.
cmd=$(jq -r '.tool_input.command // empty')

task=':?[A-Za-z][A-Za-z0-9]*(:[A-Za-z][A-Za-z0-9]*)*'
# An unquoted * would be expanded by the shell against file names, which could smuggle in a flag.
tests='--tests +("[A-Za-z0-9_.* -]+"|[A-Za-z0-9_.]+)'
flag='--console=plain|--continue|--offline|--stacktrace|--status|--rerun-tasks|--no-configuration-cache|--info|--quiet|-q|--warning-mode[= ]all'
allowed="^\./gradlew( +($task|$tests|$flag))+ *$"
device='install|connected|androidtest|baselineprofile|publish|upload|signing'

[[ $cmd =~ $allowed ]] || exit 0
shopt -s nocasematch
[[ $cmd =~ $device ]] && exit 0

echo '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow","permissionDecisionReason":"gradlew with task names and harmless flags only"}}'
