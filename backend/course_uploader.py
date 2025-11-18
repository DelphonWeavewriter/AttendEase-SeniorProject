import csv
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
from datetime import datetime
from typing import Dict, Any

#This would be different in actual use, since this is my own filepath for things to work
CSV_FILE_PATH = r"C:\Users\bramc\Downloads\coursesNew.csv"
SERVICE_ACCOUNT_KEY_PATH = r"C:\Users\bramc\Downloads\attendease-1004d-firebase-adminsdk-fbsvc-db016e4167.json"

COLLECTION_NAME = 'Courses'
DOCUMENT_ID_KEY = 'Class'

def initialize_firebase():
    try:
        key = credentials.Certificate(SERVICE_ACCOUNT_KEY_PATH)

        if not firebase_admin._apps:
            firebase_admin.initialize_app(key)
            print("Firebase Admin SDK successfully initialized.")
        return firestore.client()
    except Exception as e:
        print(f"Failed to initialize Firebase: {e}")
        return None

#Calculate semester from date
def calculate_semester(date_string: str) -> str:
    try:
        date_obj = datetime.strptime(date_string.strip(), '%m/%d/%Y')
        month = date_obj.month
        year = date_obj.year

        if 2 <= month <= 6:
            return f"Spring {year}"
        elif 7 <= month <= 8:
            return f"Summer {year}"
        elif 9 <= month <= 12:
            return f"Fall {year}"
        else:
            return f"Spring {year}"
    except:
        return ''

#One CSV row = one course
def map_row_to_document(row: Dict[str, str]) -> Dict[str, Any]:
    # split building and room since its stored in one 'cell' but has a comma for delineation
    full_location = row.get('Building & Room', '')
    building = full_location.strip()
    room = '' #in case theres no room listed (such as an online class) / asynch or w/e

    if ',' in full_location:
        piece = full_location.rsplit(',', 1)
        building = piece[0].strip()
        room = piece[1].strip()

    start_date = row.get('Start Date', '').strip()
    end_date = row.get('End Date', '').strip()

    #Calculate semester from date
    semester_name = row.get('Semester', '')
    if not semester_name and start_date:
        semester_name = calculate_semester(start_date)

    schedule_entry = {
        'building': building,
        'dayOfWeek': row.get('Day', '').strip(),
        'endTime': row.get('End Time', '').strip(),
        'room': room,
        'startTime': row.get('Start Time', '').strip(),
    }

    #convert numbers in csv from string to int, defaults to 0.
    try:
        credits = int(row.get('Credits', 0))
    except ValueError:
        credits = 0

    try:
        max_capacity = int(row.get('Max Capacity', 0))
    except ValueError:
        max_capacity = 0

    #enrolled students stays empty on creation
    enrolled_students = []

    document_data = {
        'campus': row.get('Campus', '').strip(),
        'courseId': row.get('Class', '').strip(),
        'courseName': row.get('Course Title', '').strip(),
        'credits': credits,
        'department': row.get('Department', '').strip(),
        'enrolledStudents': enrolled_students,
        'maxCapacity': max_capacity,
        'professorId': row.get('Instructor', '').strip(),
        'professorName': row.get('Professor Name', '').strip(),
        'schedule': [schedule_entry],
        'semester': row.get('Semester', '').strip(),
        'semesterStart': row.get('Start Date', '').strip(),
        'semesterEnd': row.get('End Date', '').strip()
    }

    return document_data


def upload_csv_to_firestore(db, csv_file_path, collection_name, document_id_key):
    with open(csv_file_path, mode='r', encoding='utf-8') as file:
        #use DictReader to automatically map rows to dictionaries using the header
        reader = csv.DictReader(file)

        print(f"Uploading to: '{collection_name}'...")
        print(f"Using auto-generated UUIDs for document names.")

        upload_count = 0

        for row in reader:
            document_data = map_row_to_document(row)
            #Let Firestore generate the UUID by not specifying a document ID
            doc_ref = db.collection(collection_name).document()
            doc_ref.set(document_data)

            upload_count += 1

        print(f"Finished. Total uploaded: {upload_count}")
        print(f"Check '{collection_name}' to verify the documents are there.")


db = initialize_firebase()
upload_csv_to_firestore(db, CSV_FILE_PATH, COLLECTION_NAME, DOCUMENT_ID_KEY)
