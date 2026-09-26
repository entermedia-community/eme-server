#!/bin/bash
#
# Snapshot and restore the exact commits of eme-server and every plugin checkout.
#
# Usage:
#   version.sh create <version> [-f]   # write bin/versions/<version>.json (-f overwrites)
#   version.sh <version>               # shorthand for create
#   version.sh restore <version>       # check out every component at the recorded commit
#   version.sh list                    # show the saved versions
#   version.sh status                  # confirm nothing is pending (exit 1 if something is)
#
# A version file looks like:
#   { "version": "1.1", "date": "2026-09-25 10:00:00",
#     "components": [ { "name": "eme-server", "path": ".", "repo": "...", "branch": "main", "commit": "<sha>" }, ... ] }
#
# Restore moves each component's local branch to the recorded commit (git checkout -B), so a
# later "eme.sh update" / "plugins.sh update" brings everything back to the latest. The old HEAD
# is tagged pre-restore-* first so any unpushed local commit stays reachable. A component with
# uncommitted changes is skipped.
#
# Works on Linux and macOS (bash 3.2, BSD tools). Requires git and jq.
#
# Everything lives in main(), which bash parses completely before running. That matters for
# restore: checking out an older eme-server commit may rewrite this very file mid-run.

main() {
    SERVERHOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
    VERSIONSDIR="$SERVERHOME/bin/versions"
    # Copy of the last restored version file, kept inside .git so it survives checking out an
    # older eme-server commit (which removes newer bin/versions files) and never shows as a change
    RESTOREDFILE="$SERVERHOME/.git/eme-restored-version.json"

    for tool in git jq; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            echo "ERROR: $tool is required (macOS: brew install $tool, Linux: apt install $tool)." >&2
            exit 1
        fi
    done

    local cmd="$1"
    case "$cmd" in
        create)  version_create "$2" "$3" ;;
        restore) version_restore "$2" ;;
        list)    version_list ;;
        status)  version_status ;;
        ""|help|-h|--help) usage ;;
        *)       version_create "$1" "$2" ;;
    esac
}

usage() {
    sed -n '3,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

check_version_name() {
    local version="$1"
    if [ -z "$version" ]; then
        usage
        exit 1
    fi
    case "$version" in
        *[!A-Za-z0-9._-]*|.*)
            echo "ERROR: Invalid version '$version'. Use letters, digits, '.', '_' or '-'." >&2
            exit 1
            ;;
    esac
}

##prints "name|path" for eme-server followed by each plugins/<name>.json that has a checkout
list_components() {
    echo "eme-server|."
    local configfile plugin
    for configfile in "$SERVERHOME"/plugins/*.json; do
        [ -e "$configfile" ] || continue
        plugin="$(basename "$configfile" .json)"
        echo "$plugin|plugins/$plugin"
    done
}

version_create() {
    local version="$1" force="$2"
    check_version_name "$version"

    local versionfile="$VERSIONSDIR/$version.json"
    if [ -e "$versionfile" ] && [ "$force" != "-f" ]; then
        echo "ERROR: $versionfile already exists. Pass -f to overwrite." >&2
        exit 1
    fi

    local date components line name path dir commit branch repo
    date=$(date +"%Y-%m-%d %H:%M:%S")
    components="[]"

    while IFS= read -r line; do
        name="${line%%|*}"
        path="${line#*|}"
        dir="$SERVERHOME/$path"

        if [ ! -d "$dir/.git" ]; then
            echo -e "\e[33mSkipping $name: $path is not checked out.\e[0m"
            continue
        fi

        commit=$(git -C "$dir" rev-parse HEAD)
        branch=$(git -C "$dir" rev-parse --abbrev-ref HEAD)
        [ "$branch" = "HEAD" ] && branch="main"
        repo=$(git -C "$dir" config --get remote.origin.url)

        if [ -n "$(git -C "$dir" status --porcelain --untracked-files=no)" ]; then
            echo -e "\e[33mWarning: $name has uncommitted changes; recording HEAD $commit only.\e[0m"
        fi

        components=$(jq -c \
            --arg name "$name" --arg path "$path" --arg repo "$repo" \
            --arg branch "$branch" --arg commit "$commit" \
            '. + [{name: $name, path: $path, repo: $repo, branch: $branch, commit: $commit}]' \
            <<< "$components")
        echo "$name $commit"
    done <<< "$(list_components)"

    jq -n --arg version "$version" --arg date "$date" --argjson components "$components" \
        '{version: $version, date: $date, components: $components}' > "$versionfile"

    echo "Created $versionfile"
}

##make sure $commit exists locally; plugin checkouts are shallow so it usually needs fetching
fetch_commit() {
    local dir="$1" commit="$2" branch="$3"

    git -C "$dir" cat-file -e "$commit^{commit}" 2>/dev/null && return 0
    git -C "$dir" fetch --depth=1 origin "$commit" 2>/dev/null
    git -C "$dir" cat-file -e "$commit^{commit}" 2>/dev/null && return 0

    # Server refused a fetch by SHA - fall back to the full branch history
    if [ -f "$dir/.git/shallow" ]; then
        git -C "$dir" fetch --unshallow origin "$branch"
    else
        git -C "$dir" fetch origin "$branch"
    fi
    git -C "$dir" cat-file -e "$commit^{commit}" 2>/dev/null
}

version_restore() {
    local version="$1"
    check_version_name "$version"

    local versionfile="$VERSIONSDIR/$version.json"
    if [ ! -f "$versionfile" ]; then
        echo "ERROR: $versionfile not found. Run: $0 list" >&2
        exit 1
    fi

    # Read the whole file up front; restoring eme-server may change bin/versions
    local rows
    rows=$(jq -r '.components[] | [.name, (.path // (if .name == "eme-server" then "." else "plugins/" + .name end)), (.repo // ""), (.branch // "main"), .commit] | join("|")' "$versionfile") || exit 1
    [ -d "$SERVERHOME/.git" ] && cp "$versionfile" "$RESTOREDFILE"

    local failed=false name path repo branch commit dir current
    while IFS='|' read -r name path repo branch commit; do
        [ -n "$name" ] || continue
        dir="$SERVERHOME/$path"

        if [ ! -d "$dir/.git" ]; then
            if [ -z "$repo" ]; then
                echo -e "\e[31m$name: not checked out and no repo recorded, skipping.\e[0m"
                failed=true
                continue
            fi
            echo "Cloning $name into $path"
            mkdir -p "$dir"
            git -C "$dir" init -q
            git -C "$dir" remote add origin "$repo"
        fi

        current=$(git -C "$dir" rev-parse -q --verify HEAD)
        if [ "$current" = "$commit" ]; then
            echo "$name already at $commit"
            continue
        fi

        if [ -n "$(git -C "$dir" status --porcelain --untracked-files=no)" ]; then
            echo -e "\e[31m$name has uncommitted changes, skipping. Commit or stash first.\e[0m"
            failed=true
            continue
        fi

        if ! fetch_commit "$dir" "$commit" "$branch"; then
            echo -e "\e[31m$name: could not fetch commit $commit from origin.\e[0m"
            failed=true
            continue
        fi

        if [ -n "$current" ]; then
            git -C "$dir" tag "pre-restore-$(date +%Y%m%d%H%M%S)" "$current" >/dev/null 2>&1
        fi

        if git -C "$dir" checkout -q -B "$branch" "$commit"; then
            echo -e "\e[34m$name\e[0m restored to $commit"
        else
            echo -e "\e[31m$name: checkout of $commit failed.\e[0m"
            failed=true
        fi
    done <<< "$rows"

    if [ "$failed" = true ]; then
        echo "Version $version restored with errors. Review the output above."
        exit 1
    fi
    echo "Version $version restored. Run bin/restart.sh to pick up the changes."
}

##prints "name|commit" for every component of every saved version
known_commits() {
    local file
    for file in "$VERSIONSDIR"/*.json "$RESTOREDFILE"; do
        [ -e "$file" ] || continue
        jq -r '.components[] | .name + "|" + .commit' "$file"
    done
}

##read-only: reports uncommitted changes and local commits that are not on origin, then
##which saved version (if any) matches the current checkouts. Exit 1 if anything is pending.
version_status() {
    local pending=false known line name path dir changes branch local_sha remote_sha
    known=$(known_commits)

    while IFS= read -r line; do
        name="${line%%|*}"
        path="${line#*|}"
        dir="$SERVERHOME/$path"

        if [ ! -d "$dir/.git" ]; then
            echo -e "\e[31m$name: $path is not checked out. Run: bin/plugins.sh update\e[0m"
            pending=true
            continue
        fi

        changes=$(git -C "$dir" status --porcelain)
        if [ -n "$changes" ]; then
            echo -e "\e[31m$name has uncommitted changes:\e[0m"
            echo "$changes"
            pending=true
            continue
        fi

        local_sha=$(git -C "$dir" rev-parse HEAD)
        branch=$(git -C "$dir" rev-parse --abbrev-ref HEAD)
        [ "$branch" = "HEAD" ] && branch="main"
        remote_sha=$(git -C "$dir" rev-parse -q --verify "origin/$branch")

        if [ -z "$remote_sha" ]; then
            echo -e "\e[31m$name: origin/$branch has never been fetched, cannot tell if $local_sha is pushed.\e[0m"
            pending=true
        elif [ "$local_sha" = "$remote_sha" ]; then
            echo "$name clean at $local_sha"
        elif git -C "$dir" merge-base --is-ancestor "$local_sha" "$remote_sha" 2>/dev/null; then
            echo "$name clean at $local_sha (behind origin/$branch)"
        elif echo "$known" | grep -qxF "$name|$local_sha"; then
            # A restored version: the commit came from origin, shallow history just can't prove it
            echo "$name clean at $local_sha (saved version commit)"
        else
            echo -e "\e[31m$name: $local_sha is not on last-known origin/$branch. Push it (bin/plugins.sh push or bin/eme.sh branchpush .) or pull.\e[0m"
            pending=true
        fi
    done <<< "$(list_components)"

    local file vname matches="" rows ok cname cpath ccommit
    for file in "$VERSIONSDIR"/*.json "$RESTOREDFILE"; do
        [ -e "$file" ] || continue
        rows=$(jq -r '.components[] | [.name, (.path // (if .name == "eme-server" then "." else "plugins/" + .name end)), .commit] | join("|")' "$file") || continue
        ok=true
        while IFS='|' read -r cname cpath ccommit; do
            [ -n "$cname" ] || continue
            if [ "$(git -C "$SERVERHOME/$cpath" rev-parse -q --verify HEAD 2>/dev/null)" != "$ccommit" ]; then
                ok=false
                break
            fi
        done <<< "$rows"
        vname=$(jq -r '.version // empty' "$file")
        [ -n "$vname" ] || vname=$(basename "$file" .json)
        if [ "$ok" = true ]; then
            case " $matches " in *" $vname "*) ;; *) matches="$matches $vname" ;; esac
        fi
    done
    if [ -n "$matches" ]; then
        echo "Checkouts match saved version:$matches"
    fi

    if [ "$pending" = true ]; then
        echo "Pending changes found. Commit and push them before creating or restoring a version."
        exit 1
    fi
    echo "No pending changes. Safe to create or restore a version."
}

version_list() {
    local file
    for file in "$VERSIONSDIR"/*.json; do
        [ -e "$file" ] || continue
        printf "%-12s %s\n" "$(basename "$file" .json)" "$(jq -r '.date // ""' "$file")"
    done
}

main "$@"
exit $?
