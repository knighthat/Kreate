import re


def _remove_html( text: str ) -> str:
    return re.sub(r'<[^>]+>', '', text)


def sanitize_text( text: str ) -> str:
    return _remove_html( text )