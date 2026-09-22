#!/usr/bin/env python3
"""Run the bundled DOCX renderer with a workspace-local temporary directory."""

from __future__ import annotations

import runpy
import sys
import tempfile
from pathlib import Path


RENDERER = Path(
    r"C:\Users\Windows\.codex\plugins\cache\openai-primary-runtime\documents\26.826.12353\skills\documents\render_docx.py"
)


def main() -> None:
    workspace = Path(__file__).resolve().parents[1]
    temp_root = workspace / "tmp" / "docx-render-temp"
    temp_root.mkdir(parents=True, exist_ok=True)
    tempfile.tempdir = str(temp_root)
    sys.argv = [str(RENDERER), *sys.argv[1:]]
    runpy.run_path(str(RENDERER), run_name="__main__")


if __name__ == "__main__":
    main()
