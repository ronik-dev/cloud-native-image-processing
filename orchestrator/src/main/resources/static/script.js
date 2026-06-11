const BASE_URL = 'http://localhost:8080';

// Global Reactive Memory State Containers
let selectedUserId = null;
let selectedImageId = null;

// Initialization Hook on Document Ready
document.addEventListener("DOMContentLoaded", () => {
    refreshUsers();
    refreshImagesAndJobs();
    // Start real-time background status polling intervals every 5 seconds
    setInterval(refreshImagesAndJobs, 5000);
});

// Helper Function: Extract our custom ErrorResponse JSON
async function handleApiError(response) {
    try {
        const errorData = await response.json();
        // If it's a validation error, show the specific fields, otherwise show the main message
        if (errorData.validationErrors) {
            alert(`Validation Error: ${JSON.stringify(errorData.validationErrors)}`);
        } else {
            alert(`Error: ${errorData.message}`);
        }
    } catch {
        alert("An unexpected server error occurred.");
    }
}

// ============================================================================
// LAYER 1: USER MANAGEMENT
// ============================================================================

async function refreshUsers() {
    try {
        // UPDATED: Now hits your custom UserController
        const response = await fetch(`${BASE_URL}/api/users`);
        if (!response.ok) {
				handleApiError(response);
				return;
		}
        
        const users = await response.json(); // Now returns a clean array of UserResponse DTOs
        const container = document.getElementById("usersList");
        
        container.innerHTML = users.length === 0 ? '<p style="padding:10px; margin:0;">No registered users.</p>' : '';

        users.forEach(user => {
            // UPDATED: Simply read the ID from your DTO instead of HAL links
            const parsedId = String(user.id);
            const isSelected = selectedUserId === parsedId;

            const card = document.createElement("div");
            card.className = `item-card ${isSelected ? 'selected' : ''}`;
            card.innerHTML = `
                <span><strong>${user.username}</strong> (${user.email})</span>
                <div class="item-actions">
                    <button class="btn-select ${isSelected ? 'active' : ''}" onclick="selectUser('${parsedId}')">
                        ${isSelected ? 'Selected' : 'Select'}
                    </button>
                    <button class="btn-danger" onclick="deleteUser('${parsedId}')">Delete</button>
                </div>
            `;
            container.appendChild(card);
        });
    } catch (err) {
        console.error("Failed to sync structural user graphs", err);
    }
}

async function createUser() {
    const usernameInput = document.getElementById("usernameInput");
    const emailInput = document.getElementById("emailInput");

    if (!usernameInput.value.trim() || !emailInput.value.trim()) return;

    try {
        const response = await fetch(`${BASE_URL}/api/users`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                username: usernameInput.value.trim(),
                email: emailInput.value.trim()
            })
        });

        if (response.ok) {
            usernameInput.value = '';
            emailInput.value = '';
            refreshUsers();
        } else {
            // Leverage the new GlobalExceptionHandler
            await handleApiError(response);
        }
    } catch (err) {
        console.error("Failed to register entity structure", err);
    }
}

async function deleteUser(userId) {
    if (!confirm("Are you sure you want to delete this profile?")) return;
    try {
        const response = await fetch(`${BASE_URL}/api/users/${userId}`, { method: 'DELETE' });
        if (response.ok) {
            // BUG FIX: Changed 'deletedId' to 'userId'
            if (selectedUserId === userId) {
                selectedUserId = null;
                selectedImageId = null;
            }
            refreshUsers();
            refreshImagesAndJobs();
        } else {
            await handleApiError(response);
        }
    } catch (err) {
        console.error("Constraint block encountered during structural drop", err);
    }
}

function selectUser(id) {
    selectedUserId = (selectedUserId === id) ? null : id;
    selectedImageId = null; 
    refreshUsers();
    refreshImagesAndJobs();
}

// ============================================================================
// LAYER 2: IMAGE & JOB MANAGEMENT
// ============================================================================

async function deleteImage(imageId) {
    if (!confirm("Are you sure you want to delete this image?")) return;
    try {
        const response = await fetch(`${BASE_URL}/api/images/${imageId}`, { method: 'DELETE' });
        if (response.ok) {
            if (selectedImageId === String(imageId)) selectedImageId = null;
            refreshImagesAndJobs();
        } else {
            await handleApiError(response);
        }
    } catch (err) {
        console.error("Failed to delete image asset:", err);
    }
}

async function deleteJob(jobId) {
    if (!confirm("Are you sure you want to delete this job execution tracking history?")) return;
    try {
        const response = await fetch(`${BASE_URL}/api/jobs/${jobId}`, { method: 'DELETE' });
        if (response.ok) {
            refreshImagesAndJobs();
        } else {
            await handleApiError(response);
        }
    } catch (err) {
        console.error("Failed to terminate or remove job entity:", err);
    }
}

async function refreshImagesAndJobs() {
    try {
        const imagesContainer = document.getElementById("imagesList");
        const runningJobsContainer = document.getElementById("runningJobsList");
        const finishedJobsContainer = document.getElementById("finishedJobsList");
        
        if (selectedUserId) {
            const imgResponse = await fetch(`${BASE_URL}/api/users/${selectedUserId}/images`);
            const images = imgResponse.ok ? await imgResponse.json() : [];

            let jobs = [];
            if (selectedImageId) {
                const jobResponse = await fetch(`${BASE_URL}/api/images/${selectedImageId}/jobs`);
                if (jobResponse.ok) {
                    jobs = await jobResponse.json();
                }
            }

            imagesContainer.innerHTML = images.length === 0 ? '<p style="padding:10px; margin:0;">No image assets.</p>' : '';
            runningJobsContainer.innerHTML = '';
            finishedJobsContainer.innerHTML = '';

            images.forEach(img => {
                // UPDATED: Now strictly relies on your custom DTO ID
                const imgId = String(img.id);
                const isSelected = selectedImageId === imgId;

                const imgCard = document.createElement("div");
                imgCard.className = `item-card ${isSelected ? 'selected' : ''}`;
                imgCard.innerHTML = `
                    <span>${img.filename || 'Unnamed Asset'} <small>(${img.format ? img.format.toUpperCase() : 'UNKNOWN'})</small></span>
                    <div class="item-actions">
                        <button class="btn-select ${isSelected ? 'active' : ''}" onclick="selectImage('${imgId}')">
                            ${isSelected ? 'Selected' : 'Select'}
                        </button>
                        <button class="btn-danger" onclick="deleteImage('${imgId}')">Delete</button>
                    </div>
                `;
                imagesContainer.appendChild(imgCard);
            });

            let activeCount = 0;
            let finishedCount = 0;

            jobs.forEach(job => {
                const jobCard = document.createElement("div");
                jobCard.className = "item-card";

                if (job.status === "PENDING" || job.status === "RUNNING") {
                    activeCount++;
                    jobCard.innerHTML = `
                        <span><strong>Job #${job.id}:</strong> ${job.outputName}</span>
                        <div class="item-actions">
                            <span class="status-badge status-running">${job.status}</span>
                            <button class="btn-danger" style="padding: 5px 8px; font-size: 0.8rem; width: auto;" onclick="deleteJob('${job.id}')">Delete</button>
                        </div>
                    `;
                    runningJobsContainer.appendChild(jobCard);
                } else {
                    finishedCount++;
                    let actionItem = job.status === "DONE" ? `
                        <div class="item-actions">
                            <span class="status-badge status-done">DONE</span>
                            <button class="btn-download" onclick="downloadResult('${job.id}')">Download</button>
                            <button class="btn-danger" onclick="deleteJob('${job.id}')">Delete</button>
                        </div>
                    ` : `
                        <div class="item-actions">
                            <span class="status-badge status-failed">FAILED</span>
                            <button class="btn-danger" onclick="deleteJob('${job.id}')">Delete</button>
                        </div>
                    `;

                    jobCard.innerHTML = `
                        <span><strong>Job #${job.id}:</strong> ${job.outputName}</span>
                        ${actionItem}
                    `;
                    finishedJobsContainer.appendChild(jobCard);
                }
            });

            if (activeCount === 0) runningJobsContainer.innerHTML = '<p style="padding:10px; margin:0;">No active worker executions.</p>';
            if (finishedCount === 0) finishedJobsContainer.innerHTML = '<p style="padding:10px; margin:0;">No archived output logs.</p>';
        } else {
            imagesContainer.innerHTML = '<p style="padding:10px; margin:0;">Select a user to view their assets.</p>';
            runningJobsContainer.innerHTML = '<p style="padding:10px; margin:0;">No active worker executions.</p>';
            finishedJobsContainer.innerHTML = '<p style="padding:10px; margin:0;">No archived output logs.</p>';
        }
    } catch (err) {
        console.error("Error synchronizing decoupled asset trackers:", err);
    }
}

function selectImage(id) {
    selectedImageId = (selectedImageId === id) ? null : id;
    refreshImagesAndJobs();
}

async function uploadImage() {
    if (!selectedUserId) {
        alert("Please select a User first.");
        return;
    }

    const fileInput = document.getElementById("imageFileInput");
    if (fileInput.files.length === 0) {
        alert("Please select a file to upload.");
        return;
    }

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);
    formData.append("userId", selectedUserId);

    try {
        const response = await fetch(`${BASE_URL}/api/images`, {
            method: 'POST',
            body: formData
        });

        if (response.ok) {
            fileInput.value = '';
            refreshImagesAndJobs();
        } else {
            await handleApiError(response);
        }
    } catch (err) {
        console.error("I/O multi-part transmission failure", err);
    }
}

async function triggerPipelineJob() {
    if (!selectedImageId) {
        alert("Please select an Image first.");
        return;
    }

    const operationType = document.getElementById("operationSelect").value;
    const targetFormat = document.getElementById("targetFormatSelect").value;
    const outputNameInput = document.getElementById("outputNameInput");
    const outputName = outputNameInput.value.trim();

    if (!outputName) {
        alert("Please provide a valid output name.");
        outputNameInput.focus();
        return;
    }

    const requestBody = { type: operationType, outputName: outputName, targetFormat: targetFormat };

    try {
        // STEP 1: Create the Job
        const createResponse = await fetch(`${BASE_URL}/api/images/${selectedImageId}/jobs`, { 
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });

        if (createResponse.ok) {
            const jobData = await createResponse.json();
            const jobId = jobData.id;

            // STEP 2: Trigger the Job
            const triggerResponse = await fetch(`${BASE_URL}/api/jobs/${jobId}/process`, { method: 'POST' });

            if (triggerResponse.ok) {
                // STEP 3: Start polling
                pollJobStatus(jobId);
            } else {
                await handleApiError(triggerResponse);
            }
        } else {
            await handleApiError(createResponse);
        }
    } catch (err) {
        console.error("Gateway transmission exception:", err);
    }
}

async function pollJobStatus(jobId) {
    try {
        const response = await fetch(`${BASE_URL}/api/jobs/${jobId}`);
        if (!response.ok) {
				await handleApiError(response);
				return;
		}

        const jobData = await response.json();

        if (jobData.status === 'DONE') {
            refreshImagesAndJobs(); 
        } 
        else if (jobData.status === 'FAILED') {
            alert(`Job #${jobId} failed to process.`);
            refreshImagesAndJobs();
        } 
        else {
            setTimeout(() => pollJobStatus(jobId), 2000);
        }
    } catch (error) {
        console.error("Error polling job status:", error);
    }
}

function downloadResult(jobId) {
    window.location.href = `${BASE_URL}/api/jobs/${jobId}/result`;
}
