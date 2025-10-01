import csv
import os
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore

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


def map_row_to_document(row):
    # split building and room since its stored in one 'cell' but has a comma for delineation
    full_location = row.get('Building & Room', '')
    building_id = full_location.strip()
    room_id = ''  # in case theres no room listed (such as an online exam)

    if ',' in full_location:
        piece = full_location.rsplit(',', 1)
        building_id = piece[0].strip()
        room_id = piece[1].strip()

    # map where things go, adjust if we ever change the names of attributes or collections
    document_data = {
        'Semester': row.get('Session', ''),
        'buildingId': building_id,
        'campus': row.get('Campus', ''),
        'courseId': row.get('Class', ''),
        'courseName': row.get('Course Title', ''),
        'endTime': row.get('End Time', ''),
        'professorId': row.get('Instructor', ''),
        'roomId': room_id,
        'sessionId': row.get('Session', ''),
        'startTime': row.get('Start Time', ''),
        'date': row.get('Date', ''),
    }
    return document_data


# actually uploading it
def upload_csv_to_firestore(db, csv_file_path, collection_name, document_id_key):
    with open(csv_file_path, mode='r', encoding='utf-8') as file:
        # Use DictReader to automatically map rows to dictionaries using the header
        reader = csv.DictReader(file)

        print(f"Uploading to: '{collection_name}'...")
        print(f"Column '{document_id_key}' as the Document name.")

        upload_count = 0

        for row in reader:
            document_id = row.get(document_id_key)

            if not document_id:
                print(f"Skipping row {upload_count + 1}: Missing value for document ID key '{document_id_key}'.")
                continue

            document_data = map_row_to_document(row)
            doc_ref = db.collection(collection_name).document(str(document_id))
            doc_ref.set(document_data)

            upload_count += 1

        print(f"Finished. Total uploaded: {upload_count}")
        print(f"Check '{collection_name}' to verify the documents are there.")


db = initialize_firebase()
upload_csv_to_firestore(db, CSV_FILE_PATH, COLLECTION_NAME, DOCUMENT_ID_KEY)
