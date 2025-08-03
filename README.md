# OFBiz Auth Extension Plugin

This OFBiz plugin exposes user login and tenant information as REST services for external integration, particularly with Keycloak SPI.

## Overview

The plugin provides secure REST endpoints to retrieve:
- User login information with associated party data
- Tenant/organization information
- Combined user-tenant data for external authentication systems

## Features

- **Secure REST API**: All endpoints protected by OFBiz authentication
- **User Information Service**: Get complete user profile with party details
- **Tenant Data Service**: Retrieve organization/tenant information
- **Combined Services**: User data with tenant context
- **Multi-tenant Support**: Proper tenant isolation using OFBiz delegator

## API Endpoints

### Authentication
All endpoints require valid OFBiz authentication token via `/rest/auth/token`

### User Services
- `GET /rest/auth-extension/services/getUserInfo` - Get user information by userLoginId
- `GET /rest/auth-extension/services/getUserWithTenant` - Get user with tenant context
- `GET /rest/auth-extension/services/getTenantInfo` - Get tenant/organization information
- `POST /rest/auth-extension/services/validateUserCredentials` - Validate user credentials
- `GET /rest/auth-extension/services/health` - Health check endpoint

## Installation

**This plugin is already properly installed in the OFBiz plugins directory.**

To build and run:

1. Navigate to your OFBiz root directory:
   ```bash
   cd ~/ws/ofbiz/sources/selzcore/apache-ofbiz
   ```

2. Build OFBiz (this will include the plugin):
   ```bash
   ./gradlew build
   ```

3. Start OFBiz:
   ```bash
   ./gradlew ofbiz
   ```

4. Services will be available under `/rest/auth-extension/services/`

## Configuration

No additional configuration required. The plugin uses standard OFBiz security and authentication mechanisms. OFBiz automatically loads all plugins from the `plugins/` directory.

## Usage Example

```bash
# Get authentication token
curl -X POST "https://localhost:8443/rest/auth/token" \
  -H "Authorization: Basic $(echo -n 'admin:ofbiz' | base64)" \
  -H "Content-Type: application/json"

# Use token to get user info
curl -X GET "https://localhost:8443/rest/auth-extension/services/getUserInfo?userLoginId=admin" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json"

# Validate credentials (no auth required)
curl -X POST "https://localhost:8443/rest/auth-extension/services/validateUserCredentials" \
  -H "Content-Type: application/json" \
  -d '{"userLoginId":"admin","password":"ofbiz"}'

# Health check (no auth required)
curl -X GET "https://localhost:8443/rest/auth-extension/services/health"
```

## Testing

Use the provided test script to verify the API:

```bash
./test-api.sh
```

Make sure OFBiz is running before executing the test script.

## Integration with Keycloak SPI

This plugin is designed to work with the OFBiz Keycloak SPI. The SPI can call these endpoints to:

1. **Validate credentials**: Use `validateUserCredentials` instead of direct database access
2. **Get user info**: Retrieve complete user profile with `getUserInfo`
3. **Get tenant context**: Obtain organization/tenant information with `getUserWithTenant`

## Development

The plugin follows standard OFBiz plugin structure:
- `servicedef/` - Service definitions
- `src/` - Java service implementations
- `webapp/` - Web application configuration
- `config/` - Configuration files
- `widget/` - Screen definitions

## Project Structure

```
ofbiz-auth-extension/
├── ofbiz-component.xml          # OFBiz component definition
├── servicedef/
│   └── services.xml            # Service definitions
├── src/org/apache/ofbiz/authextension/
│   └── AuthExtensionServices.java  # Service implementations
├── webapp/
│   ├── ofbiz-auth-extension/   # Main web application
│   └── rest/                   # REST API endpoints
├── widget/
│   └── AuthExtensionScreens.xml    # Screen definitions
├── config/
│   └── health.json            # Health check response
├── build.xml                  # Ant build file
├── test-api.sh               # API test script
├── README.md                 # This file
└── INSTALL.md               # Detailed installation guide
```

## License

Licensed under the Apache License 2.0
