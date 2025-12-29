from langdetect import detect_langs
from langdetect.language import Language

import re

from utils import sanitize_text


SECTION_HEADER_PATTERN = re.compile(r'### .*?\n\n')

def detect( text: str ) -> list[Language]:
    """
    Returns a list of languages detected in the [text] and how confident
    the machine thinks this language is
    """
    sanitized_text = sanitize_text( text )
    no_header = SECTION_HEADER_PATTERN.sub( '', sanitized_text )

    return detect_langs( no_header )