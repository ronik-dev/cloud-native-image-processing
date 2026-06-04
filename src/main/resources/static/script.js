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

// ============================================================================
// LAYER 1: SPRING DATA REST ENGINES (NO PREFIX - HAL JSON)
// ============================================================================

async function refreshUsers() {
		try {
				// Reads directly from the automatically exposed collection resource path
				const response = await fetch(`${BASE_URL}/users`);
				const data = await response.json();
				const users = data._embedded ? data._embedded.users : [];

				const container = document.getElementById("usersList");
				container.innerHTML = users.length === 0 ? '<p style=" padding:10px; margin:0;">No registered users.</p>' : '';

				users.forEach(user => {
						// Spring Data REST utilizes direct href URL strings inside self properties as keys
						const userSelfHref = user._links.self.href;
						const parsedId = userSelfHref.split('/').pop();
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
				const response = await fetch(`${BASE_URL}/users`, {
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
						if (selectedUserId === deletedId) {
								selectedUserId = null;
								selectedImageId = null;
						}
						refreshUsers();
						refreshImagesAndJobs();
				}
		} catch (err) {
				console.error("Constraint block encountered during structural drop", err);
		}
}

function selectUser(id) {
		// Toggle user selection off if clicked again, otherwise select
		selectedUserId = (selectedUserId === id) ? null : id;
		selectedImageId = null; // Clear down-stream asset focus automatically
		refreshUsers();
		refreshImagesAndJobs();
}

// ============================================================================
// LAYER 2: CUSTOM CONTROLLER MANAGEMENT (PREFIXED ROUTING MAPS)
// ============================================================================

async function deleteImage(imageId) {
		console.log("deleteImage called with ID:", imageId); // <-- Debug Log
		if (!confirm("Are you sure you want to delete this image?")) return;
		try {
				const response = await fetch(`${BASE_URL}/api/images/${imageId}`, { method: 'DELETE' });
				if (response.ok) {
						if (selectedImageId === String(imageId)) selectedImageId = null;
						refreshImagesAndJobs();
				}
		} catch (err) {
				console.error("Failed to delete image asset:", err);
		}
}

async function deleteJob(jobId) {
		console.log("deleteJob called with ID:", jobId); // <-- Debug Log
		if (!confirm("Are you sure you want to delete this job execution tracking history?")) return;
		try {
				const response = await fetch(`${BASE_URL}/api/jobs/${jobId}`, { method: 'DELETE' });
				console.log("Delete Job Response Status:", response.status); // <-- Debug Log
				if (response.ok) {
						refreshImagesAndJobs();
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
				// 1. Fetch filtered or global list of images
				if (selectedUserId) {
						let imgUrl = `${BASE_URL}/api/users/${selectedUserId}/images`;
						const imgResponse = await fetch(imgUrl);
						const imgData = await imgResponse.json();

						// Handle both Spring Data REST HAL arrays and standard custom JSON arrays
						const images = imgData._embedded ? imgData._embedded.images : (Array.isArray(imgData) ? imgData : []);

						// Only fetch jobs if an image is actually selected
						let jobs = [];
						if (selectedImageId) {
								let jobUrl = `${BASE_URL}/api/images/${selectedImageId}/jobs`;
								const jobResponse = await fetch(jobUrl);
								if (jobResponse.ok) {
										jobs = await jobResponse.json();
								}
						}

						imagesContainer.innerHTML = images.length === 0 ? '<p style=" padding:10px; margin:0;">No image assets.</p>' : '';
						runningJobsContainer.innerHTML = '';
						finishedJobsContainer.innerHTML = '';

						// 3. Populate Asset selection list
						images.forEach(img => {
								// Extract ID safely from self link if custom DTO property is absent (HAL reference)
								const imgId = img.id ? String(img.id) : img._links.self.href.split('/').pop();
								const isSelected = selectedImageId === imgId;

								const imgCard = document.createElement("div");
								imgCard.className = `item-card ${isSelected ? 'selected' : ''}`;
								imgCard.innerHTML = `
							<span>${img.filename || 'Unnamed Asset'} <small style="">(${img.format ? img.format.toUpperCase() : 'UNKNOWN'})</small></span>
							<div class="item-actions">
								<button class="btn-select ${isSelected ? 'active' : ''}" onclick="selectImage('${imgId}')">
									${isSelected ? 'Selected' : 'Select'}
								</button>
								<button class="btn-danger" onclick="deleteImage('${imgId}')">Delete</button>
							</div>
						`;
								imagesContainer.appendChild(imgCard);
						});

						// 4. Populate Active and Completed tracking columns
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
										let actionItem = '';

										if (job.status === "DONE") {
												actionItem = `
											<div class="item-actions">
												<span class="status-badge status-done">DONE</span>
												<button class="btn-download" onclick="downloadResult('${job.id}')">Download</button>
												<button class="btn-danger" onclick="deleteJob('${job.id}')">Delete</button>
											</div>
										`;
										} else {
												actionItem = `
											<div class="item-actions">
												<span class="status-badge status-failed">FAILED</span>
												<button class="btn-danger" onclick="deleteJob('${job.id}')">Delete</button>
											</div>
										`;
										}

										jobCard.innerHTML = `
									<span><strong>Job #${job.id}:</strong> ${job.outputName}</span>
									${actionItem}
								`;
										finishedJobsContainer.appendChild(jobCard);
								}
						});

						// If no jobs were fetched (either because no image is selected or the image has no jobs)
						if (activeCount === 0) runningJobsContainer.innerHTML = '<p style=" padding:10px; margin:0;">No active worker executions.</p>';
						if (finishedCount === 0) finishedJobsContainer.innerHTML = '<p style=" padding:10px; margin:0;">No archived output logs.</p>';
				}else {
						// Clear the UI when no user is selected
						imagesContainer.innerHTML = '<p style="padding:10px; margin:0;">Select a user to view their assets.</p>';
						runningJobsContainer.innerHTML = '<p style="padding:10px; margin:0;">No active worker executions.</p>';
						finishedJobsContainer.innerHTML = '<p style=" padding:10px; margin:0;">No archived output logs.</p>';
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
				alert("Fail-Fast Exception: You must select an active User Profile context first to assign asset bindings.");
				return;
		}

		const fileInput = document.getElementById("imageFileInput");
		if (fileInput.files.length === 0) {
				alert("Validation Fault: Please select a physical binary asset from your drive.");
				return;
		}

		const formData = new FormData();
		formData.append("file", fileInput.files[0]);
		formData.append("userId", selectedUserId);

		try {
				const response = await fetch(`${BASE_URL}/api/upload`, {
						method: 'POST',
						body: formData
				});

				if (response.ok) {
						fileInput.value = '';
						refreshImagesAndJobs();
				} else {
						alert("Storage validation failure raised from application layer gateway.");
				}
		} catch (err) {
				console.error("I/O multi-part transmission failure", err);
		}
}

async function triggerPipelineJob() {
		if (!selectedImageId) {
				alert("Validation Fault: Please select an active Image asset first.");
				return;
		}

		const operationType = document.getElementById("operationSelect").value;
		const targetFormat = document.getElementById("targetFormatSelect").value;
		const outputNameInput = document.getElementById("outputNameInput");
		const outputName = outputNameInput.value.trim();

		if (!outputName) {
				alert("Validation Fault: Please provide a valid output name for the processing task.");
				outputNameInput.focus();
				return;
		}

		const safeOutputName = encodeURIComponent(outputName);

		const endpointUrl = `${BASE_URL}/api/images/${selectedImageId}/jobs`;
		const requestBody = { type: operationType, outputName: outputName, targetFormat: targetFormat };

		try {
				// STEP 1: Create the Job
				const createResponse = await fetch(endpointUrl, { 
						method: 'POST',
						headers: { 'Content-Type': 'application/json' },
						body: JSON.stringify(requestBody)
				});

				if (createResponse.ok) {
						const jobData = await createResponse.json();
						const jobId = jobData.id;

						console.log(`Job ${jobId} created. Triggering execution...`);

						// STEP 2: Trigger the Job
						const triggerResponse = await fetch(`${BASE_URL}/api/jobs/${jobId}/process`, {
								method: 'POST'
						});

						if (triggerResponse.ok) {
								// STEP 3: Start polling
								pollJobStatus(jobId);
						} else {
								console.error("Failed to trigger execution for job:", jobId);
						}
				}
		} catch (err) {
				console.error("Gateway transmission exception:", err);
		}
}

// 4. The Polling Mechanism
async function pollJobStatus(jobId) {
		try {
				// Fetch the current state of the job
				const response = await fetch(`${BASE_URL}/api/jobs/${jobId}`);
				if (!response.ok) throw new Error("Status check failed at API gateway");

				const jobData = await response.json();

				if (jobData.status === 'DONE') {
						console.log(`Pipeline success: Job ${jobId} completed.`);
						// Final UI refresh to show the completed file and remove spinners
						refreshImagesAndJobs(); 
				} 
				else if (jobData.status === 'FAILED') {
						console.error(`Pipeline failure: Job ${jobId} encountered an error.`);
						alert("The processing job failed. Check backend logs.");
						refreshImagesAndJobs();
				} 
				else {
						// Status is PENDING or PROCESSING. 
						// Wait 2000ms (2 seconds) and recursively call this function again.
						setTimeout(() => pollJobStatus(jobId), 2000);
				}
		} catch (error) {
				console.error("Error polling job status:", error);
		}
}

function downloadResult(jobId) {
		window.location.href = `${BASE_URL}/api/jobs/${jobId}/result`;
}
