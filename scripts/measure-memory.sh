#!/usr/bin/env bash
# M0 exit criterion: every service stays within its memory limit (ADR-0016).
# Fails if any container was OOM-killed or uses more than 90% of its limit.
source "$(dirname "$0")/lib.sh"

total=0
status=0
printf '%-12s %10s %10s %6s\n' SERVICE USED_MiB LIMIT_MiB USE%
for id in $(compose ps -q); do
    name="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.service"}}' "$id")"
    [[ "$(docker inspect --format '{{.State.OOMKilled}}' "$id")" == "false" ]] \
        || { echo "$name was OOM-killed"; status=1; }
    used_bytes="$(numfmt --from=iec-i "$(docker stats --no-stream --format '{{.MemUsage}}' "$id" | awk '{sub(/B$/, "", $1); print $1}')")"
    used=$(( used_bytes / 1048576 ))
    limit=$(( $(docker inspect --format '{{.HostConfig.Memory}}' "$id") / 1048576 ))
    pct=$(( used * 100 / limit ))
    printf '%-12s %10s %10s %5s%%\n' "$name" "$used" "$limit" "$pct"
    (( pct <= 90 )) || { echo "$name above 90% of its limit"; status=1; }
    total=$(( total + used ))
done
printf '%-12s %10s\n' TOTAL "$total"
(( total < 3072 )) || { echo "stack exceeds the 3 GB budget"; status=1; }
exit $status
