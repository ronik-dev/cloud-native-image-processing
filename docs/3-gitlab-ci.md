# CI Pipeline - Sprint 1
> This guide covers the GitLab CI/CD pipeline configuration introduced in Sprint 1.
> The pipeline will be fully implemented in Sprint 4 with test, build, and deploy stages.
> This guide assumes the GitLab repository is already initialised and the project compiles successfully.

### Context
GitLab CI/CD is configured via a `.gitlab-ci.yml` file at the root of the repository.
When a commit is pushed, GitLab reads this file and executes the defined jobs on a runner.

> **Note on AutoDevOps:** GitLab may automatically trigger its built-in AutoDevOps pipeline
> when no `.gitlab-ci.yml` is present. This is disabled by adding our own file, which
> always takes precedence over AutoDevOps. AutoDevOps can also be disabled permanently via
> Settings --> CI/CD → Auto DevOps.

---

### Blockers encountered in Sprint 1

During Sprint 1 the following issues prevented a working pipeline from being configured:

**Docker Hub inaccessible from SUPSI runners**
The shared runners on `gitlab-edu.supsi.ch` cannot pull images from Docker Hub.
Any `image:` referencing `docker.io` (e.g. `maven:3.9-eclipse-temurin-21`) will time out
after one hour with `pull access denied`.

**Local shell runner not viable off-campus**
A local shell runner was considered as an alternative. It was discarded because the runner
requires continuous network access to `gitlab-edu.supsi.ch` to poll for jobs, which is not
guaranteed outside the university network.

---

### Project structure changes
```
cloud-native-image-processing/
└── .gitlab-ci.yml
```

---

### Steps

##### 1. Create the pipeline placeholder
Create `.gitlab-ci.yml` at the root of the repository with all jobs disabled:

```yaml
# CI/CD Pipeline
# Full implementation planned for Sprint 4 - CI/CD + Security milestone
# Pipeline disabled until proper runner and image are configured
#
# Blocker: SUPSI shared runners cannot pull from Docker Hub.
# Candidate image for Sprint 4: gitlab-edu.supsi.ch:5050/dti-isin/labingsw/common:maven3java17
# Requires Java version alignment (project uses Java 21, image provides Java 17).

stages:
  - test

test-job:
  stage: test
  rules:
    - when: never
  script:
    - mvn test
```

`rules: when: never` disables the job without removing it. GitLab triggers a pipeline on
every push but immediately marks it as **passed** since no jobs are active.
This keeps MRs unblocked while the blocker is resolved in Sprint 4.

> **Quality gate for Sprint 1:** run `./mvnw test` locally before every merge request.
> This replaces the automated pipeline check until Sprint 4.

##### 2. Commit and push
```bash
git add .gitlab-ci.yml
git commit -m "chore: add CI pipeline placeholder, disabled until Sprint 4"
git push
```

Open **CI/CD --> Pipelines** - the pipeline should appear and pass immediately with no jobs run.

---

### Pipeline evolution across sprints

| Sprint | Stage | Description |
|---|---|---|
| 1 | - | Placeholder file, all jobs disabled |
| 4 | `test` | Run tests against multiple JDK versions using SUPSI registry image |
| 4 | `build` | Build Docker image, push to GitLab Container Registry |
| 4 | `deploy` | Continuous delivery to GKE staging, continuous deployment to production |
