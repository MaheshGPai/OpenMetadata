/*
 *  Copyright 2021 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.service.resources.policies;

import static org.openmetadata.common.utils.CommonUtil.listOrEmpty;

import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.json.JsonPatch;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.common.utils.CommonUtil;
import org.openmetadata.schema.api.policies.CreatePolicy;
import org.openmetadata.schema.entity.policies.Policy;
import org.openmetadata.schema.type.EntityHistory;
import org.openmetadata.schema.type.EntityReference;
import org.openmetadata.schema.type.Function;
import org.openmetadata.schema.type.Include;
import org.openmetadata.schema.type.ResourceDescriptor;
import org.openmetadata.service.Entity;
import org.openmetadata.service.FunctionList;
import org.openmetadata.service.OpenMetadataApplicationConfig;
import org.openmetadata.service.ResourceRegistry;
import org.openmetadata.service.jdbi3.CollectionDAO;
import org.openmetadata.service.jdbi3.ListFilter;
import org.openmetadata.service.jdbi3.PolicyRepository;
import org.openmetadata.service.resources.Collection;
import org.openmetadata.service.resources.CollectionRegistry;
import org.openmetadata.service.resources.EntityResource;
import org.openmetadata.service.security.Authorizer;
import org.openmetadata.service.security.policyevaluator.CompiledRule;
import org.openmetadata.service.security.policyevaluator.PolicyCache;
import org.openmetadata.service.security.policyevaluator.RuleEvaluator;
import org.openmetadata.service.util.EntityUtil;
import org.openmetadata.service.util.JsonUtils;
import org.openmetadata.service.util.ResultList;

@Slf4j
@Path("/v1/policies")
@Api(value = "Policies collection", tags = "Policies collection")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Collection(name = "policies", order = 0)
public class PolicyResource extends EntityResource<Policy, PolicyRepository> {
  public static final String COLLECTION_PATH = "v1/policies/";

  @Override
  public Policy addHref(UriInfo uriInfo, Policy policy) {
    Entity.withHref(uriInfo, policy.getOwner());
    Entity.withHref(uriInfo, policy.getTeams());
    Entity.withHref(uriInfo, policy.getRoles());
    return policy;
  }

  public PolicyResource(CollectionDAO dao, Authorizer authorizer) {
    super(Policy.class, new PolicyRepository(dao), authorizer);
  }

  @SuppressWarnings("unused") // Method is used by reflection
  public void initialize(OpenMetadataApplicationConfig config) throws IOException {
    // Load any existing rules from database, before loading seed data.
    dao.initSeedDataFromResources();
    ResourceRegistry.add(listOrEmpty(PolicyResource.getResourceDescriptors()));
  }

  public static class PolicyList extends ResultList<Policy> {
    @SuppressWarnings("unused")
    PolicyList() {
      // Empty constructor needed for deserialization
    }

    public PolicyList(List<Policy> data, String beforeCursor, String afterCursor, int total) {
      super(data, beforeCursor, afterCursor, total);
    }
  }

  public static class ResourceDescriptorList extends ResultList<ResourceDescriptor> {
    @SuppressWarnings("unused")
    ResourceDescriptorList() {
      // Empty constructor needed for deserialization
    }

    public ResourceDescriptorList(List<ResourceDescriptor> data) {
      super(data, null, null, data.size());
    }
  }

  public static final String FIELDS = "owner,location,teams,roles";

  @GET
  @Valid
  @Operation(
      operationId = "listPolicies",
      summary = "List Policies",
      tags = "policies",
      description =
          "Get a list of policies. Use `fields` parameter to get only necessary fields. "
              + "Use cursor-based pagination to limit the number "
              + "entries in the list using `limit` and `before` or `after` query params.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "List of policies",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PolicyList.class)))
      })
  public ResultList<Policy> list(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(
              description = "Fields requested in the returned resource",
              schema = @Schema(type = "string", example = FIELDS))
          @QueryParam("fields")
          String fieldsParam,
      @Parameter(description = "Limit the number policies returned. (1 to 1000000, " + "default = 10)")
          @DefaultValue("10")
          @Min(0)
          @Max(1000000)
          @QueryParam("limit")
          int limitParam,
      @Parameter(description = "Returns list of policies before this cursor", schema = @Schema(type = "string"))
          @QueryParam("before")
          String before,
      @Parameter(description = "Returns list of policies after this cursor", schema = @Schema(type = "string"))
          @QueryParam("after")
          String after,
      @Parameter(
              description = "Include all, deleted, or non-deleted entities.",
              schema = @Schema(implementation = Include.class))
          @QueryParam("include")
          @DefaultValue("non-deleted")
          Include include)
      throws IOException {
    ListFilter filter = new ListFilter(include);
    return super.listInternal(uriInfo, securityContext, fieldsParam, filter, limitParam, before, after);
  }

  @GET
  @Path("/{id}")
  @Operation(
      operationId = "getPolicyByID",
      summary = "Get a policy",
      tags = "policies",
      description = "Get a policy by `id`.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The policy",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Policy.class))),
        @ApiResponse(responseCode = "404", description = "Policy for instance {id} is not found")
      })
  public Policy get(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @PathParam("id") UUID id,
      @Parameter(
              description = "Fields requested in the returned resource",
              schema = @Schema(type = "string", example = FIELDS))
          @QueryParam("fields")
          String fieldsParam,
      @Parameter(
              description = "Include all, deleted, or non-deleted entities.",
              schema = @Schema(implementation = Include.class))
          @QueryParam("include")
          @DefaultValue("non-deleted")
          Include include)
      throws IOException {
    return getInternal(uriInfo, securityContext, id, fieldsParam, include);
  }

  @GET
  @Path("/name/{fqn}")
  @Operation(
      operationId = "getPolicyByFQN",
      summary = "Get a policy by name",
      tags = "policies",
      description = "Get a policy by fully qualified name.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The policy",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Policy.class))),
        @ApiResponse(responseCode = "404", description = "Policy for instance {id} is not found")
      })
  public Policy getByName(
      @Context UriInfo uriInfo,
      @PathParam("fqn") String fqn,
      @Context SecurityContext securityContext,
      @Parameter(
              description = "Fields requested in the returned resource",
              schema = @Schema(type = "string", example = FIELDS))
          @QueryParam("fields")
          String fieldsParam,
      @Parameter(
              description = "Include all, deleted, or non-deleted entities.",
              schema = @Schema(implementation = Include.class))
          @QueryParam("include")
          @DefaultValue("non-deleted")
          Include include)
      throws IOException {
    return getByNameInternal(uriInfo, securityContext, fqn, fieldsParam, include);
  }

  @GET
  @Path("/{id}/versions")
  @Operation(
      operationId = "listAllPolicyVersion",
      summary = "List policy versions",
      tags = "policies",
      description = "Get a list of all the versions of a policy identified by `id`",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "List of policy versions",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EntityHistory.class)))
      })
  public EntityHistory listVersions(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "policy Id", schema = @Schema(type = "string")) @PathParam("id") UUID id)
      throws IOException {
    return super.listVersionsInternal(securityContext, id);
  }

  @GET
  @Path("/{id}/versions/{version}")
  @Operation(
      operationId = "getSpecificPolicyVersion",
      summary = "Get a version of the policy",
      tags = "policies",
      description = "Get a version of the policy by given `id`",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "policy",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Policy.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Policy for instance {id} and version {version} is" + " " + "not found")
      })
  public Policy getVersion(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "policy Id", schema = @Schema(type = "string")) @PathParam("id") UUID id,
      @Parameter(
              description = "policy version number in the form `major`.`minor`",
              schema = @Schema(type = "string", example = "0.1 or 1.1"))
          @PathParam("version")
          String version)
      throws IOException {
    return super.getVersionInternal(securityContext, id, version);
  }

  @GET
  @Path("/resources")
  @Operation(
      operationId = "listPolicyResources",
      summary = "Get list of policy resources used in authoring a policy.",
      tags = "policies",
      description = "Get list of policy resources used in authoring a policy.")
  public ResultList<ResourceDescriptor> listPolicyResources(
      @Context UriInfo uriInfo, @Context SecurityContext securityContext) {
    return new ResourceDescriptorList(ResourceRegistry.listResourceDescriptors());
  }

  @GET
  @Path("/functions")
  @Operation(
      operationId = "listPolicyFunctions",
      summary = "Get list of policy functions used in authoring conditions in policy rules.",
      tags = "policies",
      description = "Get list of policy functions used in authoring conditions in policy rules.")
  public ResultList<Function> listPolicyFunctions(@Context UriInfo uriInfo, @Context SecurityContext securityContext) {
    return new FunctionList(CollectionRegistry.getInstance().getFunctions(RuleEvaluator.class));
  }

  @POST
  @Operation(
      operationId = "createPolicy",
      summary = "Create a policy",
      tags = "policies",
      description = "Create a new policy.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The policy",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Policy.class))),
        @ApiResponse(responseCode = "400", description = "Bad request")
      })
  public Response create(@Context UriInfo uriInfo, @Context SecurityContext securityContext, @Valid CreatePolicy create)
      throws IOException {
    Policy policy = getPolicy(create, securityContext.getUserPrincipal().getName());
    return create(uriInfo, securityContext, policy, true);
  }

  @PATCH
  @Path("/{id}")
  @Operation(
      operationId = "patchPolicy",
      summary = "Update a policy",
      tags = "policies",
      description = "Update an existing policy using JsonPatch.",
      externalDocs = @ExternalDocumentation(description = "JsonPatch RFC", url = "https://tools.ietf.org/html/rfc6902"))
  @Consumes(MediaType.APPLICATION_JSON_PATCH_JSON)
  public Response patch(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @PathParam("id") UUID id,
      @RequestBody(
              description = "JsonPatch with array of operations",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_PATCH_JSON,
                      examples = {
                        @ExampleObject("[" + "{op:remove, path:/a}," + "{op:add, path: /b, value: val}" + "]")
                      }))
          JsonPatch patch)
      throws IOException {
    Response response = patchInternal(uriInfo, securityContext, id, patch);
    Policy policy = (Policy) response.getEntity();
    PolicyCache.getInstance().invalidatePolicy(policy.getId());
    return response;
  }

  @PUT
  @Operation(
      operationId = "createOrUpdatePolicy",
      summary = "Create or update a policy",
      tags = "policies",
      description = "Create a new policy, if it does not exist or update an existing policy.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "The policy",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Policy.class))),
        @ApiResponse(responseCode = "400", description = "Bad request")
      })
  public Response createOrUpdate(
      @Context UriInfo uriInfo, @Context SecurityContext securityContext, @Valid CreatePolicy create)
      throws IOException {
    Policy policy = getPolicy(create, securityContext.getUserPrincipal().getName());
    Response response = createOrUpdate(uriInfo, securityContext, policy, true);
    PolicyCache.getInstance().invalidatePolicy(policy.getId());
    return response;
  }

  @DELETE
  @Path("/{id}")
  @Operation(
      operationId = "deletePolicy",
      summary = "Delete a Policy",
      tags = "policies",
      description = "Delete a policy by `id`.",
      responses = {
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "404", description = "policy for instance {id} is not found")
      })
  public Response delete(
      @Context UriInfo uriInfo,
      @Context SecurityContext securityContext,
      @Parameter(description = "Hard delete the entity. (Default = `false`)")
          @QueryParam("hardDelete")
          @DefaultValue("false")
          boolean hardDelete,
      @Parameter(description = "Policy Id", schema = @Schema(type = "UUID")) @PathParam("id") UUID id)
      throws IOException {
    Response response = delete(uriInfo, securityContext, id, false, hardDelete, true);
    PolicyCache.getInstance().invalidatePolicy(id);
    return response;
  }

  @GET
  @Path("/validation/condition/{expression}")
  @Operation(
      operationId = "validateCondition",
      summary = "Validate a given condition",
      tags = "policies",
      description = "Validate a given condition expression used in authoring rules.",
      responses = {
        @ApiResponse(responseCode = "204", description = "No value is returned"),
        @ApiResponse(responseCode = "400", description = "Invalid expression")
      })
  public void validateCondition(
      @Context UriInfo uriInfo, @Context SecurityContext securityContext, @PathParam("expression") String expression) {
    CompiledRule.validateExpression(expression, Boolean.class);
  }

  private Policy getPolicy(CreatePolicy create, String user) throws IOException {
    Policy policy = copy(new Policy(), create, user).withRules(create.getRules()).withEnabled(create.getEnabled());
    if (create.getLocation() != null) {
      policy = policy.withLocation(new EntityReference().withId(create.getLocation()));
    }
    return policy;
  }

  public static List<ResourceDescriptor> getResourceDescriptors() throws IOException {
    List<String> jsonDataFiles = EntityUtil.getJsonDataResources(".*json/data/ResourceDescriptors.json$");
    if (jsonDataFiles.size() != 1) {
      LOG.warn("Invalid number of jsonDataFiles {}. Only one expected.", jsonDataFiles.size());
      return null;
    }
    String jsonDataFile = jsonDataFiles.get(0);
    try {
      String json = CommonUtil.getResourceAsStream(PolicyResource.class.getClassLoader(), jsonDataFile);
      return JsonUtils.readObjects(json, ResourceDescriptor.class);
    } catch (Exception e) {
      LOG.warn("Failed to initialize the resource descriptors from file {}", jsonDataFile, e);
    }
    return null;
  }
}
