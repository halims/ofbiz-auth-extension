/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ofbiz.authextension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.common.login.LoginServices;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * OFBiz Auth Extension Services
 * 
 * This class provides services to expose user login and tenant information
 * for external integration systems like Keycloak SPI.
 */
public class AuthExtensionServices {
    
    private static final String MODULE = AuthExtensionServices.class.getName();

    /**
     * Get user information including party details
     */
    public static Map<String, Object> getUserInfo(DispatchContext dctx, Map<String, Object> context) {
        Delegator delegator = dctx.getDelegator();
        String userLoginId = (String) context.get("userLoginId");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        
        Debug.logInfo("===== Starting getUserInfo service =====", MODULE);
        Debug.logInfo("Input parameters: userLoginId=" + userLoginId, MODULE);
        
        try {
            if (UtilValidate.isEmpty(userLoginId)) {
                Debug.logWarning("getUserInfo called with empty userLoginId", MODULE);
                return ServiceUtil.returnError("User Login ID is required");
            }
            
            Debug.logInfo("Fetching UserLogin entity for userLoginId: " + userLoginId, MODULE);
            
            // Get UserLogin
            GenericValue userLogin = EntityQuery.use(delegator)
                .from("UserLogin")
                .where("userLoginId", userLoginId)
                .queryOne();
                
            if (userLogin == null) {
                Debug.logWarning("UserLogin not found for userLoginId: " + userLoginId, MODULE);
                return ServiceUtil.returnError("User not found: " + userLoginId);
            }
            
            Debug.logInfo("UserLogin found successfully", MODULE);
            String partyId = userLogin.getString("partyId");
            Debug.logInfo("Associated partyId: " + partyId, MODULE);
            result.put("partyId", partyId);
            
            // Get current tenant ID from delegator name
            String delegatorName = delegator.getDelegatorName();
            String tenantId = extractTenantFromDelegatorName(delegatorName);
            Debug.logInfo("Extracted tenantId: " + tenantId + " from delegatorName: " + delegatorName, MODULE);
            result.put("tenantId", tenantId);
            
            Debug.logInfo("Building user info map", MODULE);
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userLoginId", userLoginId);
            userInfo.put("partyId", partyId);
            userInfo.put("tenantId", tenantId);
            userInfo.put("enabled", "Y".equals(userLogin.getString("enabled")));
            userInfo.put("hasLoggedOut", "Y".equals(userLogin.getString("hasLoggedOut")));
            
            if (UtilValidate.isNotEmpty(partyId)) {
                Debug.logInfo("Fetching Person information for partyId: " + partyId, MODULE);
                // Get Person information
                GenericValue person = EntityQuery.use(delegator)
                    .from("Person")
                    .where("partyId", partyId)
                    .queryOne();
                    
                if (person != null) {
                    String firstName = person.getString("firstName");
                    String lastName = person.getString("lastName");
                    Debug.logInfo("Person found - firstName: " + firstName + ", lastName: " + lastName, MODULE);
                    
                    result.put("firstName", firstName);
                    result.put("lastName", lastName);
                    userInfo.put("firstName", firstName);
                    userInfo.put("lastName", lastName);
                } else {
                    Debug.logInfo("No Person entity found for partyId: " + partyId, MODULE);
                }
                
                Debug.logInfo("Fetching primary email for partyId: " + partyId, MODULE);
                // Get primary email - using base entities instead of view entity
                List<GenericValue> partyContactMechPurposes = EntityQuery.use(delegator)
                    .from("PartyContactMechPurpose")
                    .where("partyId", partyId, 
                           "contactMechPurposeTypeId", "PRIMARY_EMAIL")
                    .queryList();
                
                if (UtilValidate.isNotEmpty(partyContactMechPurposes)) {
                    GenericValue pcmp = partyContactMechPurposes.get(0);
                    String contactMechId = pcmp.getString("contactMechId");
                    Debug.logInfo("Found contact mechanism ID: " + contactMechId, MODULE);
                    
                    GenericValue contactMech = EntityQuery.use(delegator)
                        .from("ContactMech")
                        .where("contactMechId", contactMechId,
                               "contactMechTypeId", "EMAIL_ADDRESS")
                        .queryOne();
                        
                    if (contactMech != null) {
                        String email = contactMech.getString("infoString");
                        Debug.logInfo("Found primary email: " + email, MODULE);
                        result.put("email", email);
                        userInfo.put("email", email);
                    } else {
                        Debug.logInfo("No email contact mechanism found for contactMechId: " + contactMechId, MODULE);
                    }
                } else {
                    Debug.logInfo("No primary email contact mechanism purpose found for partyId: " + partyId, MODULE);
                }
                
                Debug.logInfo("Fetching organization information for partyId: " + partyId, MODULE);
                // Get organization information (if user belongs to one)
                List<GenericValue> partyRelationships = EntityQuery.use(delegator)
                    .from("PartyRelationship")
                    .where("partyIdTo", partyId,
                           "partyRelationshipTypeId", "EMPLOYMENT")
                    .queryList();
                    
                if (UtilValidate.isNotEmpty(partyRelationships)) {
                    GenericValue relationship = partyRelationships.get(0);
                    String organizationPartyId = relationship.getString("partyIdFrom");
                    Debug.logInfo("Found organization relationship - organizationPartyId: " + organizationPartyId, MODULE);
                    result.put("organizationPartyId", organizationPartyId);
                    userInfo.put("organizationPartyId", organizationPartyId);
                    
                    // Get organization name
                    GenericValue partyGroup = EntityQuery.use(delegator)
                        .from("PartyGroup")
                        .where("partyId", organizationPartyId)
                        .queryOne();
                        
                    if (partyGroup != null) {
                        String organizationName = partyGroup.getString("groupName");
                        Debug.logInfo("Found organization name: " + organizationName, MODULE);
                        result.put("organizationName", organizationName);
                        userInfo.put("organizationName", organizationName);
                    } else {
                        Debug.logInfo("No PartyGroup found for organizationPartyId: " + organizationPartyId, MODULE);
                    }
                } else {
                    Debug.logInfo("No employment relationship found for partyId: " + partyId, MODULE);
                }
            } else {
                Debug.logInfo("No partyId associated with userLoginId: " + userLoginId, MODULE);
            }
            
            result.put("userInfo", userInfo);
            Debug.logInfo("Successfully completed getUserInfo service for userLoginId: " + userLoginId, MODULE);
            Debug.logInfo("===== Ending getUserInfo service =====", MODULE);
            
        } catch (GenericEntityException e) {
            Debug.logError(e, "Database error in getUserInfo service for userLoginId: " + userLoginId + " - " + e.getMessage(), MODULE);
            Debug.logError(e, "Error getting user information for: " + userLoginId, MODULE);
            return ServiceUtil.returnError("Error retrieving user information: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Get user information with complete tenant context
     */
    public static Map<String, Object> getUserWithTenant(DispatchContext dctx, Map<String, Object> context) {
        String userLoginId = (String) context.get("userLoginId");
        Boolean includeOrganization = (Boolean) context.get("includeOrganization");
        if (includeOrganization == null) includeOrganization = true;
        
        Debug.logInfo("===== Starting getUserWithTenant service =====", MODULE);
        Debug.logInfo("Input parameters: userLoginId=" + userLoginId + ", includeOrganization=" + includeOrganization, MODULE);
        
        // Get basic user info first
        Debug.logInfo("Calling getUserInfo service first", MODULE);
        Map<String, Object> userResult = getUserInfo(dctx, context);
        if (ServiceUtil.isError(userResult)) {
            Debug.logError("getUserInfo service failed: " + ServiceUtil.getErrorMessage(userResult), MODULE);
            return userResult;
        }
        
        Map<String, Object> result = ServiceUtil.returnSuccess();
        Map<String, Object> userInfo = (Map<String, Object>) userResult.get("userInfo");
        result.put("userInfo", userInfo);
        
        // Get tenant information
        String tenantId = (String) userInfo.get("tenantId");
        if (UtilValidate.isNotEmpty(tenantId)) {
            Map<String, Object> tenantContext = UtilMisc.toMap("tenantId", tenantId);
            Map<String, Object> tenantResult = getTenantInfo(dctx, tenantContext);
            
            if (ServiceUtil.isSuccess(tenantResult)) {
                result.put("tenantInfo", tenantResult.get("tenantInfo"));
            }
        }
        
        // Include organization info if requested
        if (includeOrganization) {
            String organizationPartyId = (String) userInfo.get("organizationPartyId");
            if (UtilValidate.isNotEmpty(organizationPartyId)) {
                Map<String, Object> orgContext = UtilMisc.toMap("partyId", organizationPartyId);
                Map<String, Object> orgResult = getTenantInfo(dctx, orgContext);
                
                if (ServiceUtil.isSuccess(orgResult)) {
                    result.put("organizationInfo", orgResult.get("tenantInfo"));
                }
            }
        }
        
        // Create combined info map
        Map<String, Object> combinedInfo = new HashMap<>();
        combinedInfo.putAll(userInfo);
        if (result.containsKey("tenantInfo")) {
            combinedInfo.put("tenant", result.get("tenantInfo"));
        }
        if (result.containsKey("organizationInfo")) {
            combinedInfo.put("organization", result.get("organizationInfo"));
        }
        result.put("combinedInfo", combinedInfo);
        
        Debug.logInfo("Successfully completed getUserWithTenant service", MODULE);
        Debug.logInfo("===== Ending getUserWithTenant service =====", MODULE);
        return result;
    }

    /**
     * Get tenant/organization information
     */
    public static Map<String, Object> getTenantInfo(DispatchContext dctx, Map<String, Object> context) {
        Delegator delegator = dctx.getDelegator();
        String tenantId = (String) context.get("tenantId");
        String partyId = (String) context.get("partyId");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        
        Debug.logInfo("===== Starting getTenantInfo service =====", MODULE);
        Debug.logInfo("Input parameters: tenantId=" + tenantId + ", partyId=" + partyId, MODULE);
        
        try {
            Map<String, Object> tenantInfo = new HashMap<>();
            
            // If we have a tenantId, use current delegator info
            if (UtilValidate.isNotEmpty(tenantId)) {
                Debug.logInfo("Using provided tenantId: " + tenantId, MODULE);
                tenantInfo.put("tenantId", tenantId);
                tenantInfo.put("delegatorName", delegator.getDelegatorName());
            } else {
                // Extract tenant from current delegator
                String delegatorName = delegator.getDelegatorName();
                tenantId = extractTenantFromDelegatorName(delegatorName);
                Debug.logInfo("Extracted tenantId: " + tenantId + " from delegatorName: " + delegatorName, MODULE);
                tenantInfo.put("tenantId", tenantId);
                tenantInfo.put("delegatorName", delegatorName);
            }
            
            // If we have a partyId, get organization details
            if (UtilValidate.isNotEmpty(partyId)) {
                GenericValue partyGroup = EntityQuery.use(delegator)
                    .from("PartyGroup")
                    .where("partyId", partyId)
                    .queryOne();
                    
                if (partyGroup != null) {
                    String organizationName = partyGroup.getString("groupName");
                    result.put("organizationName", organizationName);
                    result.put("organizationPartyId", partyId);
                    
                    tenantInfo.put("organizationName", organizationName);
                    tenantInfo.put("organizationPartyId", partyId);
                    
                    // Get partyTypeId from Party entity, not PartyGroup
                    GenericValue party = EntityQuery.use(delegator)
                        .from("Party")
                        .where("partyId", partyId)
                        .queryOne();
                    if (party != null) {
                        tenantInfo.put("partyTypeId", party.getString("partyTypeId"));
                    }
                }
                
                // Get party attributes
                List<GenericValue> partyAttributes = EntityQuery.use(delegator)
                    .from("PartyAttribute")
                    .where("partyId", partyId)
                    .queryList();
                    
                if (UtilValidate.isNotEmpty(partyAttributes)) {
                    Map<String, String> attributes = new HashMap<>();
                    for (GenericValue attr : partyAttributes) {
                        attributes.put(attr.getString("attrName"), attr.getString("attrValue"));
                    }
                    tenantInfo.put("attributes", attributes);
                    result.put("tenantAttributes", attributes);
                }
            }
            
            result.put("tenantInfo", tenantInfo);
            Debug.logInfo("Successfully completed getTenantInfo service", MODULE);
            Debug.logInfo("===== Ending getTenantInfo service =====", MODULE);
            
        } catch (GenericEntityException e) {
            Debug.logError(e, "Database error in getTenantInfo service - " + e.getMessage(), MODULE);
            return ServiceUtil.returnError("Error retrieving tenant information: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Validate user credentials and return user information
     */
    public static Map<String, Object> validateUserCredentials(DispatchContext dctx, Map<String, Object> context) {
        String userLoginId = (String) context.get("userLoginId");
        String password = (String) context.get("password");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        
        Debug.logInfo("===== Starting validateUserCredentials service =====", MODULE);
        Debug.logInfo("Input parameters: userLoginId=" + userLoginId + " (password hidden for security)", MODULE);
        
        try {
            if (UtilValidate.isEmpty(userLoginId) || UtilValidate.isEmpty(password)) {
                Debug.logWarning("validateUserCredentials called with empty userLoginId or password", MODULE);
                result.put("isValid", false);
                result.put("errorMessage", "Username and password are required");
                return result;
            }
            
            Debug.logInfo("Attempting authentication using OFBiz LoginServices", MODULE);
            
            // Use OFBiz's built-in authentication
            Map<String, Object> loginContext = UtilMisc.toMap(
                "login.username", userLoginId,
                "login.password", password
            );
            
            Map<String, Object> loginResult = LoginServices.userLogin(dctx, loginContext);
            
            if (ServiceUtil.isSuccess(loginResult)) {
                Debug.logInfo("Authentication successful for userLoginId: " + userLoginId, MODULE);
                result.put("isValid", true);
                
                // Get user information
                Debug.logInfo("Fetching user information after successful authentication", MODULE);
                Map<String, Object> userContext = UtilMisc.toMap("userLoginId", userLoginId);
                Map<String, Object> userInfoResult = getUserInfo(dctx, userContext);
                
                if (ServiceUtil.isSuccess(userInfoResult)) {
                    result.put("userInfo", userInfoResult.get("userInfo"));
                    result.put("tenantId", userInfoResult.get("tenantId"));
                    Debug.logInfo("User information retrieved successfully", MODULE);
                } else {
                    Debug.logWarning("Failed to retrieve user information after successful authentication", MODULE);
                }
                
                Debug.logInfo("Successfully completed validateUserCredentials for: " + userLoginId, MODULE);
            } else {
                Debug.logWarning("Authentication failed for userLoginId: " + userLoginId + " - " + ServiceUtil.getErrorMessage(loginResult), MODULE);
                result.put("isValid", false);
                result.put("errorMessage", ServiceUtil.getErrorMessage(loginResult));
            }
            
            Debug.logInfo("===== Ending validateUserCredentials service =====", MODULE);
            
        } catch (Exception e) {
            Debug.logError(e, "Exception in validateUserCredentials for userLoginId: " + userLoginId + " - " + e.getMessage(), MODULE);
            result.put("isValid", false);
            result.put("errorMessage", "Authentication error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Extract tenant ID from delegator name
     * OFBiz uses pattern: baseName#tenantId
     */
    private static String extractTenantFromDelegatorName(String delegatorName) {
        if (UtilValidate.isEmpty(delegatorName)) {
            return "default";
        }
        
        int hashIndex = delegatorName.indexOf('#');
        if (hashIndex > 0 && hashIndex < delegatorName.length() - 1) {
            return delegatorName.substring(hashIndex + 1);
        }
        
        return "default";
    }
}
