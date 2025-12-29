from langdetect.language import Language

import argparse

import bug_report
import feature_request


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description='Detect language of the provided text',
        epilog='Written by KnightHat'
    )
    parser.add_argument( 'text', type=str )
    
    group = parser.add_mutually_exclusive_group( required = True )
    group.add_argument(
        '--issue', 
        action='store_true',
        help='Processing texts with issue ticket template'
    )
    group.add_argument(
        '--request', 
        action='store_true',
        help='Processing texts with feature request template'
    )

    return parser.parse_args()


if __name__ == '__main__':
    args = _parse_args()
    detected_languages: list[Language] = []

    if args.issue:
        detected_languages += bug_report.detect( args.text )
    elif args.request:
        detected_languages += feature_request.detect( args.text )

    if len(detected_languages) == 0:
        print('Couldn\'t identify any language from the provided texts')
        exti( 1 )

    markdown: str = '| Language | Confidence |\n| -------- | ---------- |\n'
    for lang in detected_languages:
        prob: str = f'{lang.prob:.2%}'
        markdown += f'| {lang.lang:<8} | {prob:<10} |\n'
    with open( 'results.md', 'w' ) as file:
        file.write( markdown )

    lang_high_conf = max(detected_languages, key=lambda l: l.prob)
    with open( 'language.txt', 'w' ) as file:
        file.write( lang_high_conf.lang )