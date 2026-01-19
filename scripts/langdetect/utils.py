import re


def _remove_html( text: str ) -> str:
    return re.sub(r'<[^>]+>', '', text)


def sanitize_text( text: str ) -> str:
    non_html_text = _remove_html( text )
    return "\n".join(line.lstrip() for line in non_html_text.splitlines())