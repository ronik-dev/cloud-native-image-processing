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
  // 1. Create a single Load Test user
  const userPayload = JSON.stringify({ 
    username: `global_load_tester_${Date.now()}`, 
    email: "loadtest@test.com" 
  });
  
  const userRes = http.post(`${BASE_URL}/users`, userPayload, { 
    headers: { 'Content-Type': 'application/json' } 
  });
  check(userRes, { 'Setup: User created (201)': (r) => r.status === 201 });
  const userId = userRes.json('id');

  // 2. Upload the image ONCE
  const imgPayload = {
    file: http.file(imgBytes, 'penguin-test.jpg', 'image/jpeg'),
    userId: userId.toString(),
  };
  
  const imgRes = http.post(`${BASE_URL}/images`, imgPayload);
  check(imgRes, { 'Setup: Image uploaded (201)': (r) => r.status === 201 });
  const imageId = imgRes.json('id');
  
  // Pass BOTH IDs down to the Virtual Users
  return { userId, imageId };
}

// --- MAIN LOOP: Runs continuously for every Virtual User ---
export default function (data) {
  if (!data.imageId) {
      sleep(1);
      return; 
  }

  // 1. Create Job (using a unique output name to prevent collisions)
  const jobPayload = JSON.stringify({ 
      type: "BACKGROUND_REMOVAL", 
      outputName: `load_test_bg_${__VU}_${__ITER}` 
  });
  
  const jobRes = http.post(`${BASE_URL}/images/${data.imageId}/jobs`, jobPayload, { 
      headers: { 'Content-Type': 'application/json' } 
  });
  
  check(jobRes, { 'Job created (201)': (r) => r.status === 201 });
  const jobId = jobRes.json('id');

  // 2. Trigger Job
  if (jobId) {
      const processRes = http.post(`${BASE_URL}/jobs/${jobId}/process`);
      check(processRes, { 'Job triggered (202)': (r) => r.status === 202 });
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
