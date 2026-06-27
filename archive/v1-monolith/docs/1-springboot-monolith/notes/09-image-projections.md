# 4 Image Projections and Excerpts
>This guide is specific to Arch Linux. It assumes the monolith phase is active with Spring Data REST and HAL Explorer enabled.  

##### Context
By default, Spring Data REST exposes full resource entities, which can create bloated payloads.Projections and Excerpts optimize collection endpoints. A projection defines a lightweight view. An excerpt applies that view automatically to collection endpoints (e.g., /images), while leaving single-resource endpoints (e.g., /images/{id}) complete.  
##### Project Structure Changes
```
src/main/java/ch/supsi/imageprocessing/
├── entity/
│   └── Image.java                      # Updated with timestamp field
├── projection/
│   ├── ImageSummary.java               # New
│   └── JobSummary.java                 # New
└── repository/
    ├── ImageRepository.java            # Updated with excerpt configuration
    └── ProcessingJobRepository.java    # Updated with excerpt configuration
```
### Steps
##### 1. Update the Image Entity
Add an `uploadedAt` field and a JPA lifecycle hook to track when an asset is stored:
##### 2. Create JobSummary.java
Create `src/main/java/ch/supsi/imageprocessing/projection/JobSummary.java` to serialize core job data cleanly
##### 3. Create ImageSummary.java
Create `src/main/java/ch/supsi/imageprocessing/projection/ImageSummary.java`. This uses `@Value` to map keys and nests JobSummary
##### 4. Register Excerpts on Repositories
Configure the repositories to apply these projections to collection views automatically
##### 5. Run the System
```Bash
mvn clean compile
mvn spring-boot:run
``` 
##### 6. Verify in HAL Explorer
Open `http://localhost:8080/explorer`.  
- Collection View (GET /images): Elements inside _embedded.images display only filename, uploadedAt, and the nested processingJobs array. Relational fields are represented as HATEOAS links.
- Single Resource View (GET /images/{id}): Returns the complete Image entity payload (name, format, storagePath), completely bypassing the excerpt view.
