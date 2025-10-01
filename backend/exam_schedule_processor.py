import os
import zipfile
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
import csv
import re
from typing import List, Optional

# File paths, current ver uses .xlsx on my pc and outputs to my pc downloads folder, actual version would naturally
# Have to work off files stored on the server/however its hosted
input_file_path = r"C:\Users\bramc\Downloads\nyit FinalExamSchedule Spring2025 copy.xlsx"
output_file_path = r"C:\Users\bramc\Downloads\finals.csv"

def normalize_date(input_str: str) -> str:
    try:
        double_value = float(input_str)
        if looks_like_excel_date(double_value):
            return format_excel_date(double_value)
    except ValueError:
        pass

    # If it's not numeric, try to normalize it
    return parse_text_date(input_str)


def parse_text_date(input_str: str) -> str:
    trimmed_input = input_str.strip()

    # If already in mm/dd/yyyy format, keep the same
    if re.match(r'\d{1,2}/\d{1,2}/\d{4}', trimmed_input):
        return normalize_slash_date(trimmed_input)

    # Try these patterns for dates (since nyit could never make it consistent)
    # Also cause there's about twelve billion date formats it turns out
    date_patterns = [
        # "Monday, 5/15/2025" or "Monday,5/15/2025"
        re.compile(r'\w+,\s*(\d{1,2}/\d{1,2}/\d{4})'),
        # "Monday 5/15/2025"
        re.compile(r'\w+\s+(\d{1,2}/\d{1,2}/\d{4})'),
        # Just "5/15/2025"
        re.compile(r'(\d{1,2}/\d{1,2}/\d{4})'),
        # "May 15, 2025" word for month
        re.compile(r'(\w+\s+\d{1,2},\s*\d{4})'),
        # "15-May-2025" things with dashes
        re.compile(r'(\d{1,2}-\w+-\d{4})'),
        # "2025-05-15" if they did it other way cause obviously we cant agree on just one
        re.compile(r'(\d{4}-\d{1,2}-\d{1,2})')
    ]

    for pattern in date_patterns:
        match = pattern.search(trimmed_input)
        if match:
            date_string = match.group(1)
            normalized = try_parse_various_formats(date_string)
            if normalized:
                return normalized

    # If above fails, return default, hope it goes in fine
    return trimmed_input


def try_parse_various_formats(date_string: str) -> Optional[str]:
    format_patterns = [
        '%m/%d/%Y',
        '%m/%d/%Y',
        '%m/%d/%Y',
        '%m/%d/%Y',
        '%B %d, %Y',
        '%b %d, %Y',
        '%d-%b-%Y',
        '%Y-%m-%d',
        '%Y-%m-%d'
    ]

    for pattern in format_patterns:
        try:
            date = datetime.strptime(date_string, pattern)
            return date.strftime('%m/%d/%Y')
        except ValueError:
            # Try the next format
            continue

    return None


def normalize_slash_date(date_string: str) -> str:
    try:
        parts = date_string.split('/')
        if len(parts) == 3:
            month = parts[0].zfill(2)
            day = parts[1].zfill(2)
            year = parts[2]

            # Validate the date so it's not just false
            datetime(int(year), int(month), int(day))

            return f"{month}/{day}/{year}"
    except (ValueError, IndexError):
        # If parse fails, return original
        pass
    return date_string


def parse_shared_strings(zip_file: zipfile.ZipFile) -> List[str]:
    shared_strings = []
    try:
        with zip_file.open('xl/sharedStrings.xml') as f:
            tree = ET.parse(f)
            root = tree.getroot()

            # Define namespace
            ns = {'': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}

            si_nodes = root.findall('.//si', ns) or root.findall('.//si')
            for si_node in si_nodes:
                t_nodes = si_node.findall('.//t', ns) or si_node.findall('.//t')
                if t_nodes:
                    shared_strings.append(t_nodes[0].text or "")
                else:
                    shared_strings.append("")

    except KeyError:
        print("Warning: sharedStrings.xml not found in Excel file")
    except Exception as e:
        print(f"Warning: Could not parse shared strings: {e}")

    return shared_strings


def looks_like_excel_time(value: float) -> bool:
    #Excel times are between 0 and 1 and equate to 24 hours
    return 0.0 < value < 1.0 and '.' in str(value)


def looks_like_excel_date(value: float) -> bool:
    return 1.0 <= value <= 100000.0 and value == int(value)


def format_excel_time(excel_time: float) -> str:
    total_minutes = int(excel_time * 24 * 60)
    hours = total_minutes // 60
    minutes = total_minutes % 60

    # Convert to 12 hour format with AM/PM
    if hours == 0:
        display_hours = 12
    elif hours > 12:
        display_hours = hours - 12
    else:
        display_hours = hours

    am_pm = "AM" if hours < 12 else "PM"

    return f"{display_hours}:{minutes:02d}:00 {am_pm}"


def format_excel_date(excel_date: float) -> str:
    try:
        # Excel date system starts from 1900-01-01 but apparently Excel incorrectly treats 1900 as a leap year
        # So we need to account for this by using 1899-12-30 as day 1
        # It's better to brute force it like this than try and do some nonsense with conversion libraries or something
        # Just hope they dont change off xlsx format for the next one, though in theory we wouldnt need to worry
        # If we got system access
        base_date = datetime(1899, 12, 30)
        target_date = base_date + timedelta(days=int(excel_date))
        return target_date.strftime('%m/%d/%Y')
    except Exception as e:
        # If conversion fails, return the original value since it gives up, shouldnt happen
        return str(excel_date)


def parse_worksheet(zip_file: zipfile.ZipFile, shared_strings: List[str]) -> List[List[str]]:
    rows = []

    try:
        with zip_file.open('xl/worksheets/sheet1.xml') as f:
            tree = ET.parse(f)
            root = tree.getroot()

            # Define namespace
            ns = {'': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}

            row_nodes = root.findall('.//row', ns) or root.findall('.//row')

            for row_node in row_nodes:
                cell_nodes = row_node.findall('.//c', ns) or row_node.findall('.//c')
                row_data = []
                last_col_index = 0

                for cell_node in cell_nodes:
                    cell_ref = cell_node.get('r', '')
                    col_index = cell_ref_to_index(cell_ref)

                    # Fill empty columns if there are gaps
                    while last_col_index < col_index:
                        row_data.append("")
                        last_col_index += 1

                    cell_type = cell_node.get('t', '')
                    value_nodes = cell_node.findall('.//v', ns) or cell_node.findall('.//v')

                    if value_nodes:
                        value = value_nodes[0].text or ""
                        if cell_type == 's':
                            try:
                                index = int(value)
                                string_value = shared_strings[index] if index < len(shared_strings) else value
                                # Normalize dates
                                cell_value = normalize_date(string_value)
                            except (ValueError, IndexError):
                                cell_value = value
                        elif cell_type == 'str':
                            cell_value = normalize_date(value)
                        else:
                            try:
                                double_value = float(value)
                                if looks_like_excel_date(double_value):
                                    cell_value = format_excel_date(double_value)
                                elif looks_like_excel_time(double_value):
                                    cell_value = format_excel_time(double_value)
                                else:
                                    cell_value = value
                            except ValueError:
                                # Normalize as date if it's not a number
                                cell_value = normalize_date(value)
                    else:
                        cell_value = ""

                    row_data.append(cell_value)
                    last_col_index += 1

                if row_data:
                    rows.append(row_data)

    except Exception as e:
        print(f"Error parsing worksheet: {e}")
        import traceback
        traceback.print_exc()

    return rows


def cell_ref_to_index(cell_ref: str) -> int:
    letters = ''.join(c for c in cell_ref if c.isalpha())
    index = 0
    for char in letters:
        index = index * 26 + (ord(char.upper()) - ord('A') + 1)
    return index - 1


def find_header_row(data: List[List[str]]) -> int:
    target_columns = ["session", "class", "course title", "instructor", "date", "start time", "end time",
                      "building & room", "campus"]

    for i, row in enumerate(data):
        normalized_row = [cell.lower().strip() for cell in row]

        # Check if this row contains most of our target columns
        match_count = sum(1 for target in target_columns if any(target in cell for cell in normalized_row))

        # If we find at least 6 out of 9 columns, consider this the header row since nyit prefaces exam
        # Stuff with non useful info crammed into first few rows of col 1
        if match_count >= 6:
            return i

    return -1  # If header not found


def filter_columns(data: List[List[str]]) -> List[List[str]]:
    header_row_index = find_header_row(data)
    if header_row_index == -1:
        print("Warning: Could not find header row, returning all data")
        return data

    header_row = data[header_row_index]
    normalized_headers = [header.lower().strip() for header in header_row]

    # Map target columns
    target_columns = {
        "session": -1,
        "class": -1,
        "course title": -1,
        "instructor": -1,
        "date": -1,
        "start time": -1,
        "end time": -1,
        "building & room": -1,
        "campus": -1
        # I can only pray they do not change the format yet again
    }

    # Find column indices
    for i, header in enumerate(normalized_headers):
        for target in target_columns.keys():
            if target in header:
                target_columns[target] = i
                break

    # Get indices of found columns in order
    column_indices = sorted([idx for idx in target_columns.values() if idx != -1])

    if not column_indices:
        print("Warning: No target columns found, returning all data")
        return data

    print(f"Found columns at indices: {column_indices}")

    # Filter data starting from header row
    filtered_data = []

    for i in range(header_row_index, len(data)):
        row = data[i]
        filtered_row = [row[index] if index < len(row) else "" for index in column_indices]
        filtered_data.append(filtered_row)

    return filtered_data


def write_csv(data: List[List[str]], output_path: str):
    try:
        with open(output_path, 'w', newline='', encoding='utf-8') as csvfile:
            writer = csv.writer(csvfile, quoting=csv.QUOTE_MINIMAL)

            for row in data:
                writer.writerow(row)

        print(f"CSV file written to path: {output_path}")
    except Exception as e:
        print(f"Error writing CSV file: {e}")
        import traceback
        traceback.print_exc()


def convert_excel_to_csv(input_path: str, output_path: str):
    try:
        if not os.path.exists(input_path):
            print(f"Error: Input file does not exist at path: {input_path}")
            return

        print("Reading Excel file")
        with zipfile.ZipFile(input_path, 'r') as zip_file:
            print("Processing shared strings")
            shared_strings = parse_shared_strings(zip_file)

            print("Processing worksheet")
            data = parse_worksheet(zip_file, shared_strings)

        if not data:
            print("No data found in Excel file")
            return

        print("Filtering columns")
        filtered_data = filter_columns(data)

        print("Writing CSV file")
        write_csv(filtered_data, output_path)

        print("Conversion completed successfully")
        print(f"Rows processed: {len(filtered_data)}")

    except Exception as e:
        print(f"Error during conversion: {e}")
        import traceback
        traceback.print_exc()


print("Starting Excel to CSV conversion.")
convert_excel_to_csv(input_file_path, output_file_path)
