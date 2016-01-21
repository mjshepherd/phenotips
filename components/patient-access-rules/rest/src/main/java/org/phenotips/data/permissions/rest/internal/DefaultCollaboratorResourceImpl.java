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
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.permissions.rest.CollaboratorResource;
import org.phenotips.data.permissions.rest.CollaboratorsResource;
import org.phenotips.data.permissions.rest.DomainObjectFactory;
import org.phenotips.data.permissions.rest.Relations;
import org.phenotips.data.permissions.script.SecurePatientAccess;
import org.phenotips.data.rest.PatientResource;
import org.phenotips.data.rest.model.Link;
import org.phenotips.data.rest.model.PhenotipsUser;

import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.rest.XWikiResource;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.text.StringUtils;
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;

import net.sf.json.JSONObject;

/**
 *
 *
 * @version $Id$
 * @since todo
 */
@Component
@Named("org.phenotips.data.permissions.rest.internal.DefaultCollaboratorResourceImpl")
@Singleton
public class DefaultCollaboratorResourceImpl extends XWikiResource implements CollaboratorResource
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

    @Override public PhenotipsUser getCollaborator(String patientId, String collaboratorId)
    {
        this.logger.debug("Retrieving collaborator with id [{}] of patient record [{}] via REST", collaboratorId, patientId);
        Patient patient = this.getPatient(patientId);
        // checks that the user has view rights, otherwise throws an exception
        this.getCurrentUserWithRights(patient, Right.VIEW);

        try {
            PhenotipsUser result = this.factory.createCollaborator(patient, collaboratorId.trim());

            // adding links relative to this context
            result.getLinks().add(new Link().withRel(Relations.SELF).withHref(this.uriInfo.getRequestUri().toString()));
            result.getLinks().add(new Link().withRel(Relations.PATIENT_RECORD)
                .withHref(this.uriInfo.getBaseUriBuilder().path(PatientResource.class).build(patientId).toString()));
            result.getLinks().add(new Link().withRel(Relations.COLLABORATORS).withHref(
                this.uriInfo.getBaseUriBuilder().path(CollaboratorsResource.class).build(patientId).toString()));

            // todo. add permissions link

            return result;
        } catch (Exception ex) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override public Response putLevelWithJson(String json, String patientId, String collaboratorId)
    {
        // todo. will null pointer throw the correct exception
        return putCollaborator(collaboratorId.trim(), JSONObject.fromObject(json).optString("level"), patientId);
    }

    @Override public Response putLevelWithForm(String patientId, String collaboratorId)
    {
        Object levelInRequest = container.getRequest().getProperty("level");
        if (levelInRequest instanceof String) {
            String level = levelInRequest.toString().trim();
            if (StringUtils.isNotBlank(level)) {
                return putCollaborator(collaboratorId, level, patientId);
            }
        }
        this.logger.error("The id, permissions level, or both were not provided or are invalid");
        throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }

    @Override public Response deleteCollaborator(String patientId, String collaboratorId)
    {
        this.logger.debug(
            "Removing collaborator with id [{}] from patient record [{}] via REST", collaboratorId, patientId);
        Patient patient = this.getPatient(patientId);
        // checks that the user has view rights, otherwise throws an exception
        this.getCurrentUserWithRights(patient, Right.EDIT);

        PatientAccess patientAccess = new SecurePatientAccess(this.manager.getPatientAccess(patient), this.manager);
        EntityReference collaboratorReference = this.currentResolver.resolve(
            collaboratorId, EntityType.DOCUMENT, new EntityReference("XWiki", EntityType.SPACE));

        if (!patientAccess.removeCollaborator(collaboratorReference)) {
            this.logger.error("Could not remove collaborator [{}] from patient record [{}]", collaboratorId, patientId);
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }

        return Response.noContent().build();
    }

    public Response putCollaborator(String collaboratorId, String accessLevelName, String patientId)
    {
        throw new NotImplementedException();
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

}
