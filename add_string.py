#!/bin/python

import os
import xml.etree.ElementTree as ET
import re
from xml.dom import minidom


res_path: str = "composeApp/src/androidMain/res"
strings_files: dict = {}


def load_string_files() -> None:
    """
    Recursively load all "strings.xml" files in "composeApp/src/androidMain/res/values-*".
    
    Each file will append "string_files" with the following pattern - file_path: tree
    where "file_path" is path to "strings.xml" file and "tree" are list of elements inside
    this file represented by ElementTree.
    """

    for root, dirs, files in os.walk(res_path):
        if "values" in os.path.basename(root):
            file_path = os.path.join(root, "strings.xml")

            # Additional confirmation to prevent error
            if os.path.exists(file_path):
                tree: ET = ET.parse(file_path)
                strings_files[file_path] = tree


def add_string(tree: ET, name: str, value: str) -> None:
    """
    Adds a new string to the XML file if it doesn't already exist.

    :param tree: 
        elements inside a strings.xml file
    :param name: 
        ID of this string, must be unique and does NOT contain special characters.
        Exception, underscore "_" is allowed as the replacement to space " "
    :param value:
        What is going to be displayed to user
    """

    root = tree.getroot()

    # Check if the string already exists
    for string in root.findall("string"):
        if string.get("name") == name:
            print(f"String '{name}' already exists in {file_path}")
            return

    # Add the new string element
    new_string = ET.Element("string", name=name)
    new_string.text = value
    root.append(new_string)


def sanitize_name(name: str) -> str:
    """Sanitize the 'name' attribute to ensure XML compatibility."""

    # Replace invalid starting characters with an underscore if necessary
    if not re.match(r'^[A-Za-z_]', name):
        name = "_" + name

    # Replace any invalid characters with underscores
    name = re.sub(r'[^A-Za-z0-9_.-]', '_', name)
    return name


def main() -> None:
    # Get user input for string name and value
    string_name = input("Enter the name (ID) of the string: ")
    string_value = input("Enter the value of the string: ")

    for file_path, tree in strings_files.items():
        add_string(tree, sanitize_name(string_name), string_value)



if __name__ == '__main__':
    # Load all strings.xml
    load_string_files()

    # Recursively ask question to add more strings to "strings.xml" files
    # if user explicitly say Yes (Y) when asked to continue
    while True:
        main()

        continue_choice = input("Do you want to add another string? (y/N): ").strip().capitalize()
        if continue_choice != "Y" and continue_choice != "Yes":
            break

    # Save all strings.xml files
    for file_path, tree in strings_files.items():
        tree_as_str: str = ET.tostring(tree.getroot(), encoding="utf-8")
        # Re-beautify .xml file (for indentation)
        reparsed = minidom.parseString(tree_as_str)
        pretty_xml = reparsed.toprettyxml(indent="    ")
        pretty_xml = "\n".join([line for line in pretty_xml.splitlines() if line.strip()])  # Remove blank lines

        with open(file_path, 'w', encoding="utf-8") as file:
            file.write(pretty_xml)

        print(f"Saved changes to {file_path}")



