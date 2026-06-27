import pytest
import requests
import time
import base64
import concurrent.futures

BASE_URL = "http://localhost:8080/api"
state = {}

def poll_job(job_id):
    process_url = f"{BASE_URL}/jobs/{job_id}/process"
    status_url = f"{BASE_URL}/jobs/{job_id}"
    
    trigger_response = requests.post(process_url)
    assert trigger_response.status_code == 202, f"Job {job_id}: Expected 202 Accepted"
    
    print(f"\n[+] Job {job_id} triggered, polling for completion...")
    
    max_retries = 15
    poll_interval = 2  
    
    for _ in range(max_retries):
        status_response = requests.get(status_url)
        assert status_response.status_code == 200
        
        job_data = status_response.json()
        status = job_data.get("status")
        
        print(f"    -> Job {job_id} Status: {status}")
        
        # FIX 1: Changed "COMPLETED" to "DONE" to match your Java Enum!
        if status == "DONE":
            return True
        elif status == "FAILED":
            pytest.fail(f"Job {job_id} failed during processing in the ML Worker.")
            
        time.sleep(poll_interval)
        
    pytest.fail(f"Job {job_id} timed out! Worker took too long to process.")


class TestImageProcessingUserJourney:
    
    def test_01_create_user(self):
        url = f"{BASE_URL}/users"
        payload = {"username": "e2e_test_user", "email": "e2e@test.com"}
        response = requests.post(url, json=payload)
        assert response.status_code == 201
        state["user_id"] = response.json()["id"]
        print(f"\n[+] User created with ID: {state['user_id']}")

    def test_02_upload_image(self, tmp_path):
        url = f"{BASE_URL}/images"
        
        # FIX 2: A mathematically perfect, valid 1x1 transparent PNG
        dummy_image = tmp_path / "test_image.png"
        valid_png_base64 = b"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
        dummy_image.write_bytes(base64.b64decode(valid_png_base64))
        
        with open(dummy_image, "rb") as img_file:
            files = {"file": ("test_image.png", img_file, "image/png")}
            data = {"userId": state["user_id"]}
            response = requests.post(url, files=files, data=data)
            
        assert response.status_code == 201
        state["image_id"] = response.json()["id"]
        print(f"\n[+] Image uploaded with ID: {state['image_id']}")

    def test_03_create_format_conversion_job(self):
        url = f"{BASE_URL}/images/{state['image_id']}/jobs"
        payload = {"type": "FORMAT_CONVERSION", "outputName": "e2e_format_test", "targetFormat": "webp"}
        response = requests.post(url, json=payload)
        assert response.status_code == 201
        state["job_id_fc"] = response.json()["id"]

    def test_04_create_background_removal_job(self):
        url = f"{BASE_URL}/images/{state['image_id']}/jobs"
        payload = {"type": "BACKGROUND_REMOVAL", "outputName": "e2e_nobg_test"}
        response = requests.post(url, json=payload)
        assert response.status_code == 201
        state["job_id_br"] = response.json()["id"]

    def test_05_create_object_detection_job(self):
        url = f"{BASE_URL}/images/{state['image_id']}/jobs"
        payload = {"type": "OBJECT_DETECTION", "outputName": "e2e_object_test"}
        response = requests.post(url, json=payload)
        assert response.status_code == 201
        state["job_id_od"] = response.json()["id"]
    
    def test_06_process_and_poll_jobs_concurrently(self):
        job_ids = [state['job_id_fc'], state['job_id_br'], state['job_id_od']]
        print("\n[+] Launching 3 concurrent threads to process jobs...")
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
            results = list(executor.map(poll_job, job_ids))
            
        assert all(results)

    def test_07_download_results(self):
        job_ids = [state['job_id_fc'], state['job_id_br'], state['job_id_od']]
        for job_id in job_ids:
            url = f"{BASE_URL}/jobs/{job_id}/result"
            response = requests.get(url)
            assert response.status_code == 200
            assert len(response.content) > 0
            print(f"\n[+] Result image for Job {job_id} successfully downloaded!")

    def test_08_cleanup(self):
        url = f"{BASE_URL}/users/{state['user_id']}"
        response = requests.delete(url)
        assert response.status_code == 204
