import csv
import os
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
from datetime import datetime

# --- Configuration ---
# this is where the csv is, adjust once we have a hosting location on backend
CSV_FILE_PATH = r"C:\Users\bramc\Downloads\finalsTest.csv"

# the service account key that is what decides if steve pays 300000 usd in overage fees
SERVICE_ACCOUNT_KEY_PATH = r"C:\Users\bramc\Downloads\attendease-1004d-firebase-adminsdk-fbsvc-db016e4167.json"

# name of the collection to upload to
COLLECTION_NAME = 'Finals'

# what the document is named, currently takes from the class id, but if you wanna go by names just adjust to the name of that col
DOCUMENT_ID_KEY = 'Class'


def initialize_firebase():
    key = credentials.Certificate(SERVICE_ACCOUNT_KEY_PATH)

    if not firebase_admin._apps:
        firebase_admin.initialize_app(key)
        print("Firebase Admin SDK successfully initialized.")
    return firestore.client()


def calculate_semester(date_string):
    """Calculate semester based on the date (e.g., '5/16/2025' -> 'Spring 2025')"""
    try:
        date_obj = datetime.strptime(date_string, '%m/%d/%Y')
        month = date_obj.month
        year = date_obj.year

        if month in [2, 3, 4, 5, 6]:
            return f"Spring {year}"
        elif month in [7, 8]:
            return f"Summer {year}"
        elif month in [9, 10, 11, 12]:
            return f"Fall {year}"
        else:  # January
            return f"Spring {year}"
    except:
        return ''


def is_cycle_format(session_value):
    """Check if session is in 'Cycle X' format, not 'Season Year' format"""
    if not session_value:
        return False

    session_lower = session_value.lower().strip()

    # check if it contains season keywords
    seasons = ['spring', 'summer', 'fall', 'winter']
    for season in seasons:
        if season in session_lower:
            return False

    # check if it contains 'cycle' since nyit cant make a consistent final exam file
    if 'cycle' in session_lower:
        return True

    return False


def map_row_to_document(row):
    # split building and room since its stored in one 'cell' but has a comma for delineation
    full_location = row.get('Building & Room', '')
    building_id = full_location.strip()
    room_id = ''  # in case theres no room listed (such as an online exam) / english finals or w/e

    if ',' in full_location:
        piece = full_location.rsplit(',', 1)
        building_id = piece[0].strip()
        room_id = piece[1].strip()

    # Calculate semester from date
    date_value = row.get('Date', '')
    semester = calculate_semester(date_value)

    # only use session col if it's in "Cycle X" format, not "Season Year" format since again nyit is not consistent
    # only 4 rows have the cycle format, but hey maybe the future will be different we haven't a clue
    session_value = row.get('Session', '')
    session_id = session_value if is_cycle_format(session_value) else ''

    # map where things go, adjust if we ever change the names of attributes or collections
    document_data = {
        'Semester': semester,
        'buildingId': building_id,
        'campus': row.get('Campus', ''),
        'courseId': row.get('Class', ''),
        'courseName': row.get('Course Title', ''),
        'endTime': row.get('End Time', ''),
        'professorId': row.get('Instructor', ''),
        'roomId': room_id,
        'sessionId': session_id,
        'startTime': row.get('Start Time', ''),
        'date': date_value,
    }
    return document_data


# actually uploading it
def upload_csv_to_firestore(db, csv_file_path, collection_name, document_id_key):
    with open(csv_file_path, mode='r', encoding='utf-8') as file:
        # use DictReader to automatically map rows to dictionaries using the header
        reader = csv.DictReader(file)

        print(f"Uploading to: '{collection_name}'...")
        print(f"Using auto-generated UUIDs for document names.")

        upload_count = 0

        for row in reader:
            document_data = map_row_to_document(row)
            # Let Firestore generate the UUID by not specifying a document ID
            doc_ref = db.collection(collection_name).document()
            doc_ref.set(document_data)

            upload_count += 1

        print(f"Finished. Total uploaded: {upload_count}")
        print(f"Check '{collection_name}' to verify the documents are there.")


db = initialize_firebase()
upload_csv_to_firestore(db, CSV_FILE_PATH, COLLECTION_NAME, DOCUMENT_ID_KEY)
