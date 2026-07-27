#!/bin/bash

# ANSI Color Codes for terminal output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "==================================================="
echo "Phase 1: Infrastructure & Security Validation"
echo "==================================================="
echo ""

# ---------------------------------------------------------
# Test 1: Container Health & Status
# ---------------------------------------------------------
echo -n "1. Checking if all containers are running/healthy... "
# Get the status of all containers
STATUSES=$(docker compose ps --format json)

# Check if any container explicitly says "unhealthy" or "exited"
if echo "$STATUSES" | grep -qiE "unhealthy|exited"; then
  echo -e "${RED}FAILED${NC}"
  echo "Error: One or more containers crashed or failed their healthcheck."
  docker compose ps
  exit 1
else
  echo -e "${GREEN}PASSED${NC}"
fi

# ---------------------------------------------------------
# Test 2: Network Isolation (Security)
# ---------------------------------------------------------
echo -n "2. Checking internal port isolation (Postgres & Worker)... "
# Try to find mapped ports on the host for our internal services
DB_PORT=$(docker port postgres 5432 2>/dev/null)
WORKER_PORT=$(docker port worker 8082 2>/dev/null)

if [ -z "$DB_PORT" ] && [ -z "$WORKER_PORT" ]; then
  echo -e "${GREEN}PASSED${NC}"
else
  echo -e "${RED}FAILED${NC}"
  echo "Error: Internal ports are accidentally exposed to the host machine!"
  exit 1
fi

# ---------------------------------------------------------
# Test 3: API Gateway Exposure
# ---------------------------------------------------------
echo -n "3. Checking API Gateway public exposure... "
GATEWAY_PORT=$(docker port gateway 8080 2>/dev/null)

if [ -n "$GATEWAY_PORT" ]; then
  echo -e "${GREEN}PASSED${NC}"
else
  echo -e "${RED}FAILED${NC}"
  echo "Error: The Gateway port (8080) is not exposed to the host."
  exit 1
fi

# ---------------------------------------------------------
# Test 4: Non-Root User Privilege Hardening
# ---------------------------------------------------------
echo -n "4. Checking ML Worker user privileges... "
WORKER_USER=$(docker compose exec -T worker whoami)

# Trim any hidden carriage returns (common in Docker exec)
WORKER_USER=$(echo "$WORKER_USER" | tr -d '\r')

if [ "$WORKER_USER" == "appuser" ]; then
  echo -e "${GREEN}PASSED${NC}"
else
  echo -e "${RED}FAILED${NC}"
  echo "Error: Worker is running as '$WORKER_USER' instead of 'appuser'. Security violation."
  exit 1
fi

# ---------------------------------------------------------
# Test 5: Internal Network Connectivity
# ---------------------------------------------------------
echo -n "5. Checking internal container-to-container routing... "
# We exec into the Gateway container and try to ping the Orchestrator via Docker DNS
# Alpine Java images usually have 'ping' installed by default
docker compose exec -T gateway ping -c 1 orchestrator > /dev/null 2>&1

if [ $? -eq 0 ]; then
  echo -e "${GREEN}PASSED${NC}"
else
  echo -e "${RED}FAILED${NC}"
  echo "Error: The Gateway cannot reach the Orchestrator over the internal Docker network."
  exit 1
fi

echo ""
echo "==================================================="
echo -e "${GREEN}✅ ALL INFRASTRUCTURE TESTS PASSED${NC}"
echo "==================================================="
