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
import org.phenotips.data.permissions.rest.DomainObjectFactory;
import org.phenotips.data.permissions.rest.OwnerResource;
import org.phenotips.data.permissions.rest.Relations;
import org.phenotips.data.permissions.script.SecurePatientAccess;
import org.phenotips.data.rest.PatientResource;
import org.phenotips.data.rest.model.Link;
import org.phenotips.data.rest.model.PatientOwner;

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
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;

import net.sf.json.JSONObject;

/**
 * The default resource implementation for returning information about the owner of a patient record, and setting the
 * owner of a patient record.
 *
 * @version $Id$
 * @since 1.3M1
 */
@Component
@Named("org.phenotips.data.permissions.rest.internal.DefaultOwnerResourceImpl")
@Singleton
public class DefaultOwnerResourceImpl extends XWikiResource implements OwnerResource
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

    /** Needed for retrieving the `owner` parameter during the PUT request (as part of setting a new owner). */
    @Inject
    private Container container;

    @Override
    public PatientOwner getOwner(String patientId)
    {
        this.logger.debug("Retrieving patient record [{}] via REST", patientId);
        Patient patient = this.repository.getPatientById(patientId);
        if (patient == null) {
            this.logger.debug("No such patient record: [{}]", patientId);
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        User currentUser = this.users.getCurrentUser();
        if (!this.access.hasAccess(Right.VIEW, currentUser == null ? null : currentUser.getProfileDocument(),
            patient.getDocument())) {
            this.logger.debug("View access denied to user [{}] on patient record [{}]", currentUser, patientId);
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        PatientOwner result = this.factory.createPatientOwner(patient);

        // adding links relative to this context
        result.getLinks().add(new Link().withRel(Relations.SELF).withHref(this.uriInfo.getRequestUri().toString()));
        result.getLinks().add(new Link().withRel(Relations.PATIENT_RECORD)
            .withHref(this.uriInfo.getBaseUriBuilder().path(PatientResource.class).build(patientId).toString()));

        // todo. add permissions link

        return result;
    }

    @Override public Response putOwnerWithJson(String json, String patientId)
    {
        try {
            String id = JSONObject.fromObject(json).getString("id");
            return putOwner(id, patientId);
        } catch (Exception ex) {
            this.logger.error("The json was not properly formatted", ex.getMessage());
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
    }

    @Override public Response putOwnerWithForm(String patientId)
    {
        Object ownerIdInRequest = container.getRequest().getProperty("owner");
        if (ownerIdInRequest instanceof String) {
            String ownerId = ownerIdInRequest.toString();
            if (StringUtils.isNotBlank(ownerId)) {
                return putOwner(ownerId, patientId);
            }
        }
        this.logger.error("The owner id was not provided or is invalid");
        throw new WebApplicationException(Status.BAD_REQUEST);
    }

    private Response putOwner(String ownerId, String patientId)
    {
        if (StringUtils.isBlank(ownerId)) {
            this.logger.error("The owner id was not provided");
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
        this.logger.debug("Setting owner of the patient record [{}] to [{}] via REST", patientId, ownerId);
        Patient patient = this.repository.getPatientById(patientId);
        if (patient == null) {
            this.logger.debug("No such patient record: [{}]", patientId);
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        User currentUser = this.users.getCurrentUser();
        if (!this.access.hasAccess(Right.EDIT, currentUser == null ? null : currentUser.getProfileDocument(),
            patient.getDocument())) {
            this.logger.debug("Edit access denied to user [{}] on patient record [{}]", currentUser, patientId);
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        // fixme. the resolver resolves to 'data' space

        EntityReference ownerReference =
            this.currentResolver.resolve(ownerId, EntityType.DOCUMENT, new EntityReference("XWiki", EntityType.SPACE));
        PatientAccess patientAccess = new SecurePatientAccess(this.manager.getPatientAccess(patient), this.manager);
        // fixme. there should be a check for current user being the owner
        // existence and validity of the passed in owner should be checked by .setOwner
        if (!patientAccess.setOwner(ownerReference)) {
            // todo. should this status be an internal server error, or a bad request?
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }

        return Response.noContent().build();
    }
}
