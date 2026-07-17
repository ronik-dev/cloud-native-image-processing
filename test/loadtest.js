import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '3m', target: 5 }, 
    { duration: '30s', target: 0 },
  ],
};

const imgBytes = open('./penguin-test.jpg', 'b');
const BASE_URL = 'http://api.imageprocessing.local/api';

// --- SETUP: Runs exactly ONCE before the test starts ---
export function setup() {
  const userPayload = JSON.stringify({ 
    username: `global_load_tester`, 
    email: "loadtest@test.com" 
  });
  
  const userRes = http.post(`${BASE_URL}/users`, userPayload, { 
    headers: { 'Content-Type': 'application/json' } 
  });
  
  check(userRes, { 'Setup: User created (201)': (r) => r.status === 201 });
  
  // Pass the user ID down to the Virtual Users
  return { userId: userRes.json('id') };
}

// --- MAIN LOOP: Runs continuously for every Virtual User ---
export default function (data) {
  // Use the ID created in the setup phase
  const userId = data.userId;

  if (!userId) {
      sleep(1);
      return; 
  }

  // 1. Upload Image
  const imgPayload = {
    file: http.file(imgBytes, 'penguin-test.jpg', 'image/jpeg'),
    userId: userId.toString(),
  };
  
  const imgRes = http.post(`${BASE_URL}/images`, imgPayload);
  check(imgRes, { 'Image uploaded (201)': (r) => r.status === 201 });
  const imageId = imgRes.json('id');

  // 2. Create and Trigger Job
  if (imageId) {
      const jobPayload = JSON.stringify({ 
          type: "BACKGROUND_REMOVAL", 
          outputName: `load_test_bg_${__ITER}` 
      });
      
      const jobRes = http.post(`${BASE_URL}/images/${imageId}/jobs`, jobPayload, { 
          headers: { 'Content-Type': 'application/json' } 
      });
      
      check(jobRes, { 'Job created (201)': (r) => r.status === 201 });
      const jobId = jobRes.json('id');

      if (jobId) {
          const processRes = http.post(`${BASE_URL}/jobs/${jobId}/process`);
          check(processRes, { 'Job triggered (202)': (r) => r.status === 202 });
      }
  }

  sleep(1);
}

// --- TEARDOWN: Runs exactly ONCE after the test finishes ---
export function teardown(data) {
  if (data.userId) {
    const delRes = http.del(`${BASE_URL}/users/${data.userId}`);
    check(delRes, { 'Teardown: User deleted (204)': (r) => r.status === 204 });
  }
}
