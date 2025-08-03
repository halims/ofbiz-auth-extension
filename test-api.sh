#!/bin/bash

# OFBiz Auth Extension - Test Script
# Tests the REST API endpoints to verify functionality

set -e

# Configuration
OFBIZ_URL="https://localhost:8443"
USERNAME="admin"
PASSWORD="ofbiz"
USER_LOGIN_ID="admin"

echo "=== OFBiz Auth Extension API Test ==="
echo "Testing against: $OFBIZ_URL"
echo

# Function to make authenticated requests
make_request() {
    local method=$1
    local endpoint=$2
    local data=$3
    local auth_header=$4
    
    echo "→ $method $endpoint"
    
    if [ -n "$data" ]; then
        if [ -n "$auth_header" ]; then
            curl -s -X "$method" "$OFBIZ_URL$endpoint" \
                -H "Content-Type: application/json" \
                -H "Authorization: $auth_header" \
                -d "$data" | jq .
        else
            curl -s -X "$method" "$OFBIZ_URL$endpoint" \
                -H "Content-Type: application/json" \
                -d "$data" | jq .
        fi
    else
        if [ -n "$auth_header" ]; then
            curl -s -X "$method" "$OFBIZ_URL$endpoint" \
                -H "Authorization: $auth_header" | jq .
        else
            curl -s -X "$method" "$OFBIZ_URL$endpoint" | jq .
        fi
    fi
    echo
}

# Check if jq is available
if ! command -v jq &> /dev/null; then
    echo "Error: jq is required for JSON formatting. Please install jq."
    exit 1
fi

echo "1. Testing Health Endpoint (no auth required)"
make_request GET "/rest/auth-extension/services/health"

echo "2. Getting Authentication Token"
AUTH_RESPONSE=$(curl -s -X POST "$OFBIZ_URL/rest/auth/token" \
    -H "Authorization: Basic $(echo -n "$USERNAME:$PASSWORD" | base64)" \
    -H "Content-Type: application/json")

echo "$AUTH_RESPONSE" | jq .

TOKEN=$(echo "$AUTH_RESPONSE" | jq -r '.access_token // .data.access_token // empty')

if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
    echo "Error: Failed to get authentication token"
    echo "Response: $AUTH_RESPONSE"
    exit 1
fi

echo "✓ Got token: ${TOKEN:0:20}..."
echo

AUTH_HEADER="Bearer $TOKEN"

echo "3. Testing getUserInfo"
make_request GET "/rest/auth-extension/services/getUserInfo?userLoginId=$USER_LOGIN_ID" "" "$AUTH_HEADER"

echo "4. Testing getUserWithTenant"
make_request GET "/rest/auth-extension/services/getUserWithTenant?userLoginId=$USER_LOGIN_ID&includeOrganization=true" "" "$AUTH_HEADER"

echo "5. Testing getTenantInfo"
make_request GET "/rest/auth-extension/services/getTenantInfo?tenantId=default" "" "$AUTH_HEADER"

echo "6. Testing validateUserCredentials (no auth required)"
CREDENTIALS='{"userLoginId":"'$USER_LOGIN_ID'","password":"'$PASSWORD'"}'
make_request POST "/rest/auth-extension/services/validateUserCredentials" "$CREDENTIALS"

echo "=== All tests completed ==="
echo
echo "If you see JSON responses above, the API is working correctly!"
echo "If you see HTML error pages, check:"
echo "  - OFBiz is running"
echo "  - Plugin is properly installed"
echo "  - REST endpoints are accessible"
echo "  - Authentication credentials are correct"
