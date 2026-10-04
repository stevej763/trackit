#!/bin/sh
# Runs in the compose "backup" service: a pg_dump of the whole database every
# BACKUP_INTERVAL_HOURS into /backups (./backups on the host), deleting dumps
# older than BACKUP_KEEP_DAYS. Connection settings come from the PG* variables.
#
# Restore one with:
#   docker compose exec -T db pg_restore -U trackit -d trackit --clean --if-exists \
#     < backups/trackit-20261004T043000Z.dump
set -eu

interval_hours="${BACKUP_INTERVAL_HOURS:-24}"
keep_days="${BACKUP_KEEP_DAYS:-14}"

while true; do
  file="/backups/trackit-$(date -u +%Y%m%dT%H%M%SZ).dump"

  # Written under a temporary name and renamed when complete, so a dump cut
  # short by a restart never looks like a good one.
  if pg_dump --format=custom --file="$file.partial"; then
    mv "$file.partial" "$file"
    echo "backup: wrote $file ($(du -h "$file" | cut -f1))"
  else
    rm -f "$file.partial"
    echo "backup: pg_dump failed; keeping the previous dumps" >&2
  fi

  find /backups -name 'trackit-*.dump' -mtime +"$keep_days" -delete

  sleep $((interval_hours * 3600))
done
