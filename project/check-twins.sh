#!/usr/bin/env bash
# project/junit and project/testng are one code base with two runners (ADR 0014): everything in src/main except the
# runner glue — the top-level classes of the assembly package and the TestNG listener registration — must be
# byte-identical. Prints what differs; fails if anything else does. Run from anywhere: project/check-twins.sh
set -euo pipefail
cd "$(dirname "$0")"

# The runner glue: assembly/*.java (not assembly/scope, the test scope is shared) and META-INF/services
glue='^src/main/java/com/study/project/assembly/[^/]+\.java$|^src/main/resources/META-INF(/services)?(/|$)'

# "Files junit/X and testng/X differ" | "Only in junit/DIR: NAME" -> X, DIR/NAME relative to the module
paths=$(diff -rq junit/src/main testng/src/main | sed -E \
    -e 's#^Files junit/([^ ]+) and .* differ$#\1#' \
    -e 's#^Only in (junit|testng)/([^:]+): (.+)$#\2/\3#' | sort -u || true)

echo "Runner glue (differs on purpose):"
echo "$paths" | grep -E "$glue" | sed 's/^/  /' || echo "  (nothing)"

unexpected=$(echo "$paths" | grep -vE "$glue" | grep -v '^$' || true)
if [ -n "$unexpected" ]; then
    echo "Differs outside the runner glue (make both twins equal, or move it into assembly/):" >&2
    echo "$unexpected" | sed 's/^/  /' >&2
    exit 1
fi
echo "Everything else in src/main is identical."
