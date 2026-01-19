from langdetect import detect_langs
from langdetect.language import Language

import re

from utils import sanitize_text


def extract_section( text: str, section_header: str ) -> str | None:
    """
    Extracts text of a section, ends when new section detected or at EOF
    """
    pattern = re.compile(rf'{re.escape(section_header)}\n\n(.*?)(?=\n\n### |\Z)', re.DOTALL)
    match = pattern.search(text)
    if match:
        return match.group(1).strip()
    else:
        return None

def detect( text: str ) -> list[Language]:
    """
    Returns a list of languages detected in the [text] and how confident
    the machine thinks this language is
    """
    sanitized_text = sanitize_text( text )
    section_text = extract_section( sanitized_text, '### Tell us how to trigger this bug' )
    return detect_langs( section_text )
