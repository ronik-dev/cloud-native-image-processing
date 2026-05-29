# CI Pipeline  Sprint 1
> This guide covers the minimal GitLab CI/CD pipeline introduced in Sprint 1.
> The pipeline will be expanded in Sprint 4 with build and deploy stages.
> This guide assumes the GitLab repository is already initialised and the project compiles successfully.

### Context
GitLab CI/CD is configured via a `.gitlab-ci.yml` file at the root of the repository.
When a commit is pushed, GitLab reads this file and executes the defined jobs on a runner.

In Sprint 1 the pipeline has a single purpose: **run the test suite on every push** so
broken code is caught before it reaches `dev`.

> **Note on AutoDevOps:** GitLab may automatically trigger its built-in AutoDevOps pipeline
> when no `.gitlab-ci.yml` is present. This was disabled by adding our own file, which
> always takes precedence over AutoDevOps. AutoDevOps can also be disabled permanently via
> Settings --> CI/CD --> Auto DevOps.

---

### Project structure changes
```
cloud-native-image-processing/
└── .gitlab-ci.yml
```

---

### Steps

##### 1. Create the pipeline file
Create `.gitlab-ci.yml` at the root of the repository:
```yaml
image: maven:3.9-eclipse-temurin-21

stages:
  - test

test-job:
  stage: test
  cache:
    key: "$CI_PROJECT_ID-maven"
    paths:
      - .m2/repository
  variables:
    MAVEN_OPTS: "-Dmaven.repo.local=.m2/repository"
  script:
    - mvn test -B
```

Configuration breakdown:

| Field | Purpose |
|---|---|
| `image` | Docker image used by the runner  provides Java 21 and Maven 3.9 (same version as the one used for development (see docs/1)|
| `stages` | Defines the pipeline stages in order |
| `cache` | Persists the Maven local repository between pipeline runs  avoids re-downloading ~100MB of dependencies every time |
| `MAVEN_OPTS` | Redirects Maven's local repo to `.m2/repository` inside the project, which is the path GitLab can cache |
| `mvn test -B` | Runs all tests in batch mode (`-B` disables colour output and interactive prompts for cleaner CI logs) |

##### 2. Verify the pipeline runs
Push the file to the feature branch:
```bash
git add .gitlab-ci.yml
git commit -m "message"
git push
```
Open the GitLab project --> **CI/CD --> Pipelines**.
The pipeline should appear and the `test-job` should pass with output ending in:
```
[INFO] Tests run: n, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

##### 3. Pipeline evolution across sprints
The pipeline will grow progressively:
The current file is intentionally minimal do not add build or deploy stages before the
Docker and Kubernetes infrastructure is in place (Sprint 2–3).
