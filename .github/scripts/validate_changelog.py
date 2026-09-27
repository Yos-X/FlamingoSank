#!/usr/bin/env python3
"""Validates and reads Pear's CHANGELOG.md.

Usage:
  validate_changelog.py validate <path>
  validate_changelog.py latest   <path>
  validate_changelog.py section  <path> <version>

"validate" checks the whole file and exits non-zero with a clear message on
the first problem found. "latest" prints the newest released version (the
first non-"[Unreleased]" section, since sections must already be ordered
newest-to-oldest). "section" prints just the categories/entries for one
released version, formatted for use as a GitHub Release body.
"""
import re
import sys

VALID_CATEGORIES = ("Additions", "Changes", "Fixes", "Other")
VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")
VERSION_HEADING_RE = re.compile(r"^## \[(.+)]\s*$")
CATEGORY_HEADING_RE = re.compile(r"^### (.+?)\s*$")
BULLET_RE = re.compile(r"^\* .+$")


class ChangelogError(Exception):
    pass


def parse(path):
    """Returns an ordered list of (version, {category: [entries]}).

    Raises ChangelogError on the first rule violation. Sections are kept in
    file order, which is also what the ordering check below relies on.
    """
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()

    if not any(line.strip() == "# Changelog" for line in lines):
        raise ChangelogError("Missing '# Changelog' heading.")

    sections = []
    current_version = None
    current_category = None
    seen_versions = set()
    first_heading_seen = False

    for i, raw in enumerate(lines, start=1):
        line = raw.rstrip()

        heading = VERSION_HEADING_RE.match(line)
        if heading:
            version = heading.group(1)

            if version in seen_versions:
                raise ChangelogError(f"Line {i}: duplicate version section '[{version}]'.")
            seen_versions.add(version)

            if not first_heading_seen:
                first_heading_seen = True
                if version != "Unreleased":
                    raise ChangelogError("'[Unreleased]' must be the first version section.")
            elif version != "Unreleased" and not VERSION_RE.match(version):
                raise ChangelogError(
                    f"Line {i}: '{version}' is not a valid MAJOR.MINOR.PATCH version."
                )

            sections.append((version, {}))
            current_version = version
            current_category = None
            continue

        category = CATEGORY_HEADING_RE.match(line)
        if category:
            name = category.group(1)
            if current_version is None:
                raise ChangelogError(f"Line {i}: category heading found outside a version section.")
            if name not in VALID_CATEGORIES:
                raise ChangelogError(f"Line {i}: unsupported category '{name}'.")
            sections[-1][1][name] = []
            current_category = name
            continue

        if not line.strip():
            continue

        if line.strip() == "# Changelog":
            continue

        if line.lstrip().startswith("#"):
            raise ChangelogError(f"Line {i}: unexpected heading '{line.strip()}'.")

        if current_version is None:
            if BULLET_RE.match(line):
                raise ChangelogError(f"Line {i}: changelog entry outside a valid version section.")
            continue  # intro prose above the first version section, e.g. the file's tagline

        if current_category is None:
            raise ChangelogError(f"Line {i}: content found outside a valid category section.")

        if not BULLET_RE.match(line):
            raise ChangelogError(f"Line {i}: entry does not use the '* ' format.")

        sections[-1][1][current_category].append(line.strip()[2:].strip())

    if not first_heading_seen:
        raise ChangelogError("Missing '[Unreleased]' section.")

    for version, categories in sections:
        for name, entries in categories.items():
            if not entries:
                raise ChangelogError(f"'{name}' under '[{version}]' has no entries.")

    released = [v for v, _ in sections if v != "Unreleased"]
    parsed = [tuple(int(p) for p in v.split(".")) for v in released]
    if parsed != sorted(parsed, reverse=True):
        raise ChangelogError("Released versions are not ordered from newest to oldest.")

    return sections


def render_section(categories):
    parts = []
    for name in VALID_CATEGORIES:
        entries = categories.get(name)
        if not entries:
            continue
        parts.append(f"### {name}")
        parts.extend(f"* {e}" for e in entries)
        parts.append("")
    return "\n".join(parts).strip()


def main(argv):
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        return 2

    command, path = argv[1], argv[2]

    try:
        sections = parse(path)
    except ChangelogError as e:
        print(f"::error::{e}", file=sys.stderr)
        return 1
    except FileNotFoundError:
        print(f"::error::{path} not found.", file=sys.stderr)
        return 1

    if command == "validate":
        print("CHANGELOG.md is valid.")
        return 0

    if command == "latest":
        released = [v for v, _ in sections if v != "Unreleased"]
        if not released:
            print("::error::CHANGELOG.md has no released version section.", file=sys.stderr)
            return 1
        print(released[0])
        return 0

    if command == "section":
        if len(argv) < 4:
            print("Usage: validate_changelog.py section <path> <version>", file=sys.stderr)
            return 2
        version = argv[3]
        for v, categories in sections:
            if v == version:
                print(render_section(categories))
                return 0
        print(f"::error::No '[{version}]' section found in CHANGELOG.md.", file=sys.stderr)
        return 1

    print(f"Unknown command '{command}'.", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
