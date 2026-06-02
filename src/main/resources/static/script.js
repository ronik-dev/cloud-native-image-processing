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
				container.innerHTML = users.length === 0 ? '<p style="color:#64748b; padding:10px; margin:0;">No registered users.</p>' : '';

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
					<button class="btn-danger" onclick="deleteUser('${userSelfHref}')">Delete</button>
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

async function deleteUser(selfHref) {
		if (!confirm("Are you sure you want to delete this profile?")) return;
		try {
				const response = await fetch(selfHref, { method: 'DELETE' });
				if (response.ok) {
						const deletedId = selfHref.split('/').pop();
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
        console.log("Delete Image Response Status:", response.status); // <-- Debug Log
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
				// 1. Fetch filtered or global list of images
				if (selectedUserId) {
						imgUrl = `${BASE_URL}/api/users/${selectedUserId}/images`;
						const imgResponse = await fetch(imgUrl);
						const imgData = await imgResponse.json();

						// Handle both Spring Data REST HAL arrays and standard custom JSON arrays
						const images = imgData._embedded ? imgData._embedded.images : (Array.isArray(imgData) ? imgData : []);

						// 2. Fetch filtered or global list of processing jobs
						let jobUrl = `${BASE_URL}/api/jobs`;
						if (selectedImageId) {
								jobUrl = `${BASE_URL}/api/images/${selectedImageId}/jobs`;
						}
						const jobResponse = await fetch(jobUrl);
						const jobs = await jobResponse.json();

						const imagesContainer = document.getElementById("imagesList");
						const runningJobsContainer = document.getElementById("runningJobsList");
						const finishedJobsContainer = document.getElementById("finishedJobsList");

						imagesContainer.innerHTML = images.length === 0 ? '<p style="color:#64748b; padding:10px; margin:0;">No image assets.</p>' : '';
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
							<span>${img.filename || 'Unnamed Asset'} <small style="color:#64748b;">(${img.format ? img.format.toUpperCase() : 'UNKNOWN'})</small></span>
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

						if (activeCount === 0) runningJobsContainer.innerHTML = '<p style="color:#64748b; padding:10px; margin:0;">No active worker executions.</p>';
						if (finishedCount === 0) finishedJobsContainer.innerHTML = '<p style="color:#64748b; padding:10px; margin:0;">No archived output logs.</p>';

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

		const targetFormat = document.getElementById("targetFormatSelect").value;
		const outputName = "output_" + selectedImageId;

		try {
				// Single unified call creates AND safely fires off execution via your updated backend logic
				const response = await fetch(`${BASE_URL}/api/images/${selectedImageId}/newjob?outputName=${outputName}&targetFormat=${targetFormat}`, {
						method: 'POST'
				});

				if (response.ok) {
						selectedImageId = null; 
						refreshImagesAndJobs();
				} else {
						alert("Could not initialize processing task pipeline context.");
				}
		} catch (err) {
				console.error("Service communication block during job creation", err);
		}
}

function downloadResult(jobId) {
		window.location.href = `${BASE_URL}/api/jobs/${jobId}/result`;
}
