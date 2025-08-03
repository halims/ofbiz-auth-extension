# OFBiz Auth Extension - Installation Guide

## Overview

The OFBiz Auth Extension plugin provides secure REST API endpoints to expose user login and tenant information for external integration systems like Keycloak SPI.

## Installation Steps

### 1. Copy Plugin to OFBiz

Copy this entire `ofbiz-auth-extension` directory to your OFBiz installation's `plugins/` directory:

```bash
cp -r ofbiz-auth-extension $OFBIZ_HOME/plugins/
```

### 2. Update Component Load Configuration

Add the component to your `plugins/component-load.xml` file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<component-loader xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:noNamespaceSchemaLocation="https://ofbiz.apache.org/dtds/component-loader.xsd">
    <!-- Other components... -->
    <load-component component-location="ofbiz-auth-extension"/>
</component-loader>
```

### 3. Build the Plugin

Navigate to your OFBiz installation and build the plugin:

```bash
cd $OFBIZ_HOME
./gradlew build
```

Or if using Ant:

```bash
cd $OFBIZ_HOME/plugins/ofbiz-auth-extension
ant build
```

### 4. Restart OFBiz

Restart your OFBiz instance to load the new plugin:

```bash
cd $OFBIZ_HOME
./gradlew ofbiz
```

### 5. Verify Installation

Once OFBiz is running, verify the plugin is loaded by:

1. **Check the main interface**: Visit `https://localhost:8443/ofbiz-auth-extension/control/main`
2. **Test health endpoint**: `GET https://localhost:8443/rest/auth-extension/services/health`

## Configuration

### Security Permissions

The plugin uses standard OFBiz security. Users need appropriate permissions:

- `OFBTOOLS_VIEW` - To access the main interface
- `COMMON` - For REST API access
- Valid user login for authentication

### REST API Endpoints

All endpoints are available under `/rest/auth-extension/services/`:

| Endpoint | Method | Auth Required | Description |
|----------|--------|---------------|-------------|
| `/getUserInfo` | GET | Yes | Get user information by userLoginId |
| `/getUserWithTenant` | GET | Yes | Get user with complete tenant context |
| `/getTenantInfo` | GET | Yes | Get tenant/organization information |
| `/validateUserCredentials` | POST | No | Validate user credentials |
| `/health` | GET | No | Health check endpoint |

### Authentication

REST endpoints use OFBiz's standard authentication:

1. **Get Token**: `POST /rest/auth/token` with Basic Authentication
2. **Use Token**: Include `Authorization: Bearer <token>` header in subsequent requests

## Usage Examples

### 1. Get Authentication Token

```bash
curl -X POST "https://localhost:8443/rest/auth/token" \
  -H "Authorization: Basic $(echo -n 'admin:ofbiz' | base64)" \
  -H "Content-Type: application/json"
```

Response:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### 2. Get User Information

```bash
curl -X GET "https://localhost:8443/rest/auth-extension/services/getUserInfo?userLoginId=admin" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json"
```

Response:
```json
{
  "userInfo": {
    "userLoginId": "admin",
    "partyId": "admin",
    "tenantId": "default",
    "enabled": true,
    "hasLoggedOut": false,
    "firstName": "Admin",
    "lastName": "User",
    "email": "admin@example.com",
    "organizationPartyId": "Company",
    "organizationName": "Demo Company"
  }
}
```

### 3. Get User with Tenant Context

```bash
curl -X GET "https://localhost:8443/rest/auth-extension/services/getUserWithTenant?userLoginId=admin&includeOrganization=true" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json"
```

### 4. Validate User Credentials

```bash
curl -X POST "https://localhost:8443/rest/auth-extension/services/validateUserCredentials" \
  -H "Content-Type: application/json" \
  -d '{
    "userLoginId": "admin",
    "password": "ofbiz"
  }'
```

Response:
```json
{
  "isValid": true,
  "userInfo": {
    "userLoginId": "admin",
    "partyId": "admin",
    "tenantId": "default"
  },
  "tenantId": "default"
}
```

## Integration with Keycloak SPI

This plugin is designed to work with the OFBiz Keycloak SPI. The SPI can call these endpoints to:

1. **Validate credentials**: Use `validateUserCredentials` instead of direct database access
2. **Get user info**: Retrieve complete user profile with `getUserInfo`
3. **Get tenant context**: Obtain organization/tenant information with `getUserWithTenant`

## Security Considerations

1. **HTTPS Only**: All endpoints require HTTPS in production
2. **Authentication**: Most endpoints require valid OFBiz authentication
3. **Authorization**: Users need appropriate permissions
4. **Rate Limiting**: Consider implementing rate limiting for production
5. **Logging**: All access is logged through OFBiz's logging system

## Troubleshooting

### Common Issues

1. **Plugin not loading**: Check `plugins/component-load.xml` configuration
2. **Authentication errors**: Verify OFBiz user credentials and permissions
3. **Service not found**: Ensure OFBiz has been restarted after installation
4. **JSON parsing errors**: Verify request format and Content-Type headers

### Logs

Check OFBiz logs for detailed error information:

```bash
tail -f $OFBIZ_HOME/runtime/logs/ofbiz.log
```

Plugin-specific logs will include `AuthExtensionServices` in the module name.

## Development

### Adding New Services

1. Define service in `servicedef/services.xml`
2. Implement in `src/org/apache/ofbiz/authextension/AuthExtensionServices.java`
3. Add request mapping in `webapp/rest/WEB-INF/controller.xml`
4. Rebuild and restart OFBiz

### Customization

The plugin can be customized for specific requirements:

- Add new user attributes
- Include additional tenant information
- Implement custom authentication logic
- Add caching mechanisms

## Support

For issues and questions:

1. Check OFBiz documentation
2. Review plugin logs
3. Verify configuration
4. Test with simple endpoints first

## License

Licensed under the Apache License 2.0. See LICENSE file for details.
