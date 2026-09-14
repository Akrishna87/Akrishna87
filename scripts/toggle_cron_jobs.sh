#!/usr/bin/env bash
#
# toggle_cron_jobs.sh
#
# Comments out or uncomments a specific set of cron job lines inside a
# crontab-style file. Run this directly on each server (e.g. as a
# harness.io step) — it never connects to any other host.
#
# You will be prompted for:
#   1. The path to the cron file to edit.
#   2. The path to a "job list" file: one exact cron line per line,
#      written in its ACTIVE (uncommented) form. These are the specific
#      jobs to act on — everything else in the cron file is left alone.
#   3. A server name/label (used only for the backup filename and logs).
#   4. Whether to comment or uncomment those jobs.
#
# For each job line, the script checks its current state first:
#   - Asked to comment a job that's already commented  -> skipped.
#   - Asked to uncomment a job that's already uncommented -> skipped.
#   - Job text not found in the cron file at all -> reported, skipped.
# Only jobs that actually need a change are touched.
#
# A timestamped backup of the cron file is taken before any edit, and at
# the end you're asked whether to install the file with `crontab` so the
# change takes effect immediately.

set -euo pipefail

# ---------------------------------------------------------------------------
# Small logging helpers so the output below is easy to scan.
# ---------------------------------------------------------------------------
log_info()  { printf '[INFO]  %s\n' "$1"; }
log_warn()  { printf '[WARN]  %s\n' "$1"; }
log_error() { printf '[ERROR] %s\n' "$1" >&2; }

# ---------------------------------------------------------------------------
# Prompt helpers
# ---------------------------------------------------------------------------

# Repeatedly asks for a file path until an existing, readable file is given.
prompt_for_existing_file() {
  local prompt_text="$1"
  local path
  while true; do
    read -rp "$prompt_text" path
    if [[ -f "$path" && -r "$path" ]]; then
      printf '%s' "$path"
      return 0
    fi
    log_error "No readable file at '$path'. Try again."
  done
}

# Repeatedly asks until the user types "comment" or "uncomment".
prompt_for_mode() {
  local mode
  while true; do
    read -rp "Mode - type 'comment' or 'uncomment': " mode
    if [[ "$mode" == "comment" || "$mode" == "uncomment" ]]; then
      printf '%s' "$mode"
      return 0
    fi
    log_error "Please type exactly 'comment' or 'uncomment'."
  done
}

# ---------------------------------------------------------------------------
# Take a timestamped backup of the cron file before touching it.
# ---------------------------------------------------------------------------
backup_cron_file() {
  local cron_file="$1"
  local server_name="$2"
  local backup_dir
  backup_dir="$(dirname "$cron_file")/cron_backups"
  mkdir -p "$backup_dir"

  local timestamp
  timestamp="$(date +%Y%m%d-%H%M%S)"
  local backup_path="$backup_dir/$(basename "$cron_file").${server_name}.${timestamp}.bak"

  cp "$cron_file" "$backup_path"
  log_info "Backup saved to $backup_path"
}

# ---------------------------------------------------------------------------
# Comment or uncomment a single job line inside the cron file, but only
# after checking its current state.
#
# Arguments: cron_file  mode ("comment"/"uncomment")  job_line
# ---------------------------------------------------------------------------
toggle_job() {
  local cron_file="$1"
  local mode="$2"
  local job_line="$3"
  local commented_line="#${job_line}"

  local has_commented=false
  local has_uncommented=false
  grep -qFx -- "$commented_line" "$cron_file" && has_commented=true
  grep -qFx -- "$job_line" "$cron_file" && has_uncommented=true

  local from_line to_line already_ok
  if [[ "$mode" == "comment" ]]; then
    from_line="$job_line"; to_line="$commented_line"
    already_ok="$has_commented"
  else
    from_line="$commented_line"; to_line="$job_line"
    already_ok="$has_uncommented"
  fi

  if [[ "$already_ok" == true ]]; then
    log_warn "Already ${mode}ed, skipping: $job_line"
    return
  fi

  local target_present
  [[ "$mode" == "comment" ]] && target_present="$has_uncommented" || target_present="$has_commented"
  if [[ "$target_present" != true ]]; then
    log_error "Not found in cron file, skipping: $job_line"
    return
  fi

  # Rewrite the file, replacing only the first line that matches exactly.
  local tmp_file
  tmp_file="$(mktemp)"
  awk -v from="$from_line" -v to="$to_line" '
    !done && $0 == from { print to; done = 1; next }
    { print }
  ' "$cron_file" > "$tmp_file"
  cp "$tmp_file" "$cron_file"
  rm -f "$tmp_file"

  log_info "${mode^}ed: $job_line"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
  local cron_file job_list_file server_name mode

  cron_file="$(prompt_for_existing_file "Path to the cron file to edit: ")"
  job_list_file="$(prompt_for_existing_file "Path to the job list file: ")"
  read -rp "Server name/label (for the backup filename): " server_name
  mode="$(prompt_for_mode)"

  backup_cron_file "$cron_file" "$server_name"

  log_info "Processing jobs from $job_list_file (mode: $mode) ..."
  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local job_line="$raw_line"

    # Skip blank lines and notes (lines starting with # in the LIST file).
    [[ -z "${job_line// }" ]] && continue
    [[ "$job_line" == \#* ]] && continue

    toggle_job "$cron_file" "$mode" "$job_line"
  done < "$job_list_file"

  log_info "Done."

  read -rp "Install this file with 'crontab' now so the change takes effect? [y/N]: " install_now
  if [[ "$install_now" == "y" || "$install_now" == "Y" ]]; then
    crontab "$cron_file"
    log_info "Installed via crontab."
  else
    log_info "Skipped install. Cron file was only edited on disk: $cron_file"
  fi
}

main "$@"
