/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package org.phenotips.data.permissions.rest.internal;

import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.Collaborator;
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.permissions.internal.DefaultCollaborator;
import org.phenotips.data.permissions.rest.CollaboratorsResource;
import org.phenotips.data.permissions.rest.DomainObjectFactory;
import org.phenotips.data.permissions.rest.Relations;
import org.phenotips.data.permissions.script.SecurePatientAccess;
import org.phenotips.data.rest.PatientResource;
import org.phenotips.data.rest.model.Collaborators;
import org.phenotips.data.rest.model.Link;

import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.rest.XWikiResource;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 *
 * @version $Id$
 * @since todo
 */
@Component
@Named("org.phenotips.data.permissions.rest.internal.DefaultCollaboratorsResourceImpl")
@Singleton
public class DefaultCollaboratorsResourceImpl extends XWikiResource implements CollaboratorsResource
{
    @Inject
    private Logger logger;

    @Inject
    private PatientRepository repository;

    @Inject
    private AuthorizationManager access;

    @Inject
    private UserManager users;

    /** Fills in missing reference fields with those from the current context document to create a full reference. */
    @Inject
    @Named("current")
    private EntityReferenceResolver<String> currentResolver;

    @Inject
    private DomainObjectFactory factory;

    @Inject
    private PermissionsManager manager;

    @Inject
    private Container container;

    @Override
    public Collaborators getCollaborators(String patientId)
    {
        this.logger.debug("Retrieving collaborators of patient record [{}] via REST", patientId);
        Patient patient = this.getPatient(patientId);
        // checks that the user has view rights, otherwise throws an exception
        this.getCurrentUserWithRights(patient, Right.VIEW);

        Collaborators result = this.factory.createCollaborators(patient, this.uriInfo);

        // factor these out as common
        result.withLinks(new Link().withRel(Relations.SELF).withHref(this.uriInfo.getRequestUri().toString()),
            new Link().withRel(Relations.PATIENT_RECORD)
                .withHref(this.uriInfo.getBaseUriBuilder().path(PatientResource.class).build(patientId).toString()));

        // todo. put permissions link

        return result;
    }

    @Override public Response postCollaboratorWithJson(String json, String patientId)
    {
        try {
            CollaboratorInfo info = this.collaboratorInfoFromJson(JSONObject.fromObject(json));
            return postCollaborator(info.id, info.level, patientId);
        } catch (Exception ex) {
            this.logger.error("The json was not properly formatted", ex.getMessage());
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
    }

    @Override public Response postCollaboratorWithForm(String patientId)
    {
        Object idInRequest = container.getRequest().getProperty("collaborator");
        Object levelInRequest = container.getRequest().getProperty("level");
        if (idInRequest instanceof String && levelInRequest instanceof String) {
            String id = idInRequest.toString().trim();
            String level = levelInRequest.toString().trim();
            if (StringUtils.isNotBlank(id) && StringUtils.isNotBlank(level)) {
                return postCollaborator(id, level, patientId);
            }
        }
        this.logger.error("The id, permissions level, or both were not provided or are invalid");
        throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }

    @Override public Response deleteCollaborators(String patientId)
    {
        return this.updateCollaborators(new LinkedList<Collaborator>(), patientId);
    }

    @Override public Response putCollaborators(String json, String patientId)
    {
        List<Collaborator> collaborators = this.jsonToCollaborators(json);
        return this.updateCollaborators(collaborators, patientId);
    }

    public Response postCollaborator(String collaboratorId, String accessLevelName, String patientId)
    {
        this.checkCollaboratorInfo(collaboratorId, accessLevelName);

        this.logger.debug(
            "Adding collaborator [{}] with permission level [{}] to the patient record [{}] via REST",
            collaboratorId, accessLevelName, patientId);
        Patient patient = this.getPatient(patientId);
        // checking that the current user has rights
        User currentUser = this.getCurrentUserWithRights(patient, Right.EDIT);
        PatientAccess patientAccess = new SecurePatientAccess(this.manager.getPatientAccess(patient), this.manager);

        // will throw an error if something goes wrong
        this.addCollaborator(collaboratorId, accessLevelName.trim(), patientAccess);
        return Response.noContent().build();
    }

    private Response updateCollaborators(Collection<Collaborator> collaborators, String patientId)
    {
        Patient patient = this.getPatient(patientId);
        // checking that the current user has rights
        this.getCurrentUserWithRights(patient, Right.EDIT);
        PatientAccess patientAccess = new SecurePatientAccess(this.manager.getPatientAccess(patient), this.manager);

        if (!patientAccess.updateCollaborators(collaborators)) {
            this.logger.error("Could not update collaborators");
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        return Response.noContent().build();
    }

    private void addCollaborator(String id, String levelName, PatientAccess patientAccess)
        throws WebApplicationException
    {
        // checking that the access level is valid
        AccessLevel level = this.getAccessLevelFromString(levelName);
        EntityReference collaboratorReference = this.currentResolver.resolve(
            id, EntityType.DOCUMENT, new EntityReference("XWiki", EntityType.SPACE));

        // todo. function .addCollaborator has to check if the collaborator already exists before adding them
        if (!patientAccess.addCollaborator(collaboratorReference, level)) {
            // todo. should this status be an internal server error, or a bad request?
            this.logger.error("Could not add a collaborator [{}] with access level [{}]", id, levelName);
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Patient getPatient(String patientId)
    {
        Patient patient = this.repository.getPatientById(patientId);
        if (patient == null) {
            this.logger.debug("No such patient record: [{}]", patientId);
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return patient;
    }

    private AccessLevel getAccessLevelFromString(String accessLevelName)
    {
        for (AccessLevel accessLevelOption : this.manager.listAccessLevels())
        {
            if (StringUtils.equalsIgnoreCase(accessLevelOption.getName(), accessLevelName)) {
                return accessLevelOption;
            }
        }
        this.logger.error("The access level name does not match any available levels");
        throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }

    private void checkCollaboratorInfo(String collaboratorId, String levelName)
    {
        if (StringUtils.isBlank(collaboratorId)) {
            this.logger.error("The collaborator id was not provided");
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
        if (StringUtils.isBlank(levelName)) {
            this.logger.error("The permissions level was not provided");
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
    }

    private User getCurrentUserWithRights(Patient patient, Right right)
    {
        User currentUser = this.users.getCurrentUser();
        if (!this.access.hasAccess(right, currentUser == null ? null : currentUser.getProfileDocument(),
            patient.getDocument())) {
            this.logger.debug("{} access denied to user [{}] on patient record [{}]",
                right, currentUser, patient.getId());
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
        return currentUser;
    }

    private CollaboratorInfo collaboratorInfoFromJson(final JSONObject json)
    {
        return new CollaboratorInfo(json.optString("id"), json.optString("level"));
    }

    private List<Collaborator> jsonToCollaborators(String json)
    {
        List<Collaborator> collaborators = new LinkedList<>();
        JSONArray collaboratorsArray = JSONArray.fromObject(json);
        for (Object collaboratorObject : collaboratorsArray) {
            // todo. will this give an internal server error if it crashes?
            CollaboratorInfo collaboratorInfo =
                this.collaboratorInfoFromJson(JSONObject.fromObject(collaboratorObject));
            this.checkCollaboratorInfo(collaboratorInfo.id, collaboratorInfo.level);

            // todo. feels like this chunk should not be in the endpoint
            EntityReference collaboratorReference = this.currentResolver.resolve(
                collaboratorInfo.id, EntityType.DOCUMENT, new EntityReference("XWiki", EntityType.SPACE));
            AccessLevel level = this.getAccessLevelFromString(collaboratorInfo.level);
            Collaborator collaborator = new DefaultCollaborator(collaboratorReference, level, null);
            collaborators.add(collaborator);
        }
        return collaborators;
    }

    private class CollaboratorInfo
    {
        String id;
        String level;

        public CollaboratorInfo(String id, String level)
        {
            this.id = id;
            this.level = level;
        }
    }
}
