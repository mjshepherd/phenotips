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
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.permissions.rest.DomainObjectFactory;
import org.phenotips.data.permissions.rest.Relations;
import org.phenotips.data.permissions.rest.VisibilityResource;
import org.phenotips.data.permissions.script.SecurePatientAccess;
import org.phenotips.data.rest.PatientResource;
import org.phenotips.data.rest.model.Link;
import org.phenotips.data.rest.model.PatientVisibility;

import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
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

import org.slf4j.Logger;

import net.sf.json.JSONObject;

/**
 *
 *
 * @version $Id$
 * @since todo
 */
@Component
@Named("org.phenotips.data.permissions.rest.internal.DefaultVisibilityResourceImpl")
@Singleton
public class DefaultVisibilityResourceImpl extends XWikiResource implements VisibilityResource
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
    public PatientVisibility getVisibility(String patientId)
    {
        this.logger.debug("Retrieving patient record [{}] via REST", patientId);
        Patient patient = this.repository.getPatientById(patientId);
        if (patient == null) {
            this.logger.debug("No such patient record: [{}]", patientId);
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        User currentUser = this.users.getCurrentUser();
        if (!this.access.hasAccess(Right.VIEW, currentUser == null ? null : currentUser.getProfileDocument(),
            patient.getDocument())) {
            this.logger.debug("View access denied to user [{}] on patient record [{}]", currentUser, patientId);
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }

        PatientVisibility result = this.factory.createPatientVisibility(patient);

        result.withLinks(new Link().withRel(Relations.SELF).withHref(this.uriInfo.getRequestUri().toString()),
            new Link().withRel(Relations.PATIENT_RECORD)
                .withHref(this.uriInfo.getBaseUriBuilder().path(PatientResource.class).build(patientId).toString()));

        // todo. put permissions link

        return result;
    }

    @Override
    public Response putVisibilityWithJson(String json, String patientId)
    {
        try {
            String visibility = JSONObject.fromObject(json).getString("level");
            return putVisibility(visibility, patientId);
        } catch (Exception ex) {
            this.logger.error("The json was not properly formatted", ex.getMessage());
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
    }

    @Override
    public Response putVisibilityWithForm(String patientId)
    {
        Object visibilityInRequest = container.getRequest().getProperty("visibility");
        if (visibilityInRequest instanceof String) {
            String visibility = visibilityInRequest.toString();
            if (StringUtils.isNotBlank(visibility)) {
                return putVisibility(visibility, patientId);
            }
        }
        this.logger.error("The visibility level was not provided or is invalid");
        throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }

    public Response putVisibility(String visibilityNameRaw, String patientId)
    {
        if (StringUtils.isBlank(visibilityNameRaw)) {
            this.logger.error("The visibility level was not provided");
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
        String visibilityName = visibilityNameRaw.trim();
        // checking that the visibility level is valid,
        Visibility visibility = null;
        for (Visibility visibilityOption : this.manager.listVisibilityOptions())
        {
            if (StringUtils.equalsIgnoreCase(visibilityOption.getName(), visibilityName)) {
                visibility = visibilityOption;
                break;
            }
        }
        if (visibility == null) {
            this.logger.error("The visibility level does not match any available levels", patientId, visibilityName);
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        this.logger.debug(
            "Setting owner of the patient record [{}] visibility to [{}] via REST", patientId, visibilityName);
        Patient patient = this.repository.getPatientById(patientId);
        if (patient == null) {
            this.logger.debug("No such patient record: [{}]", patientId);
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        User currentUser = this.users.getCurrentUser();
        if (!this.access.hasAccess(Right.EDIT, currentUser == null ? null : currentUser.getProfileDocument(),
            patient.getDocument())) {
            this.logger.debug("Edit access denied to user [{}] on patient record [{}]", currentUser, patientId);
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }

        PatientAccess patientAccess = new SecurePatientAccess(this.manager.getPatientAccess(patient), this.manager);
        if (!patientAccess.setVisibility(visibility)) {
            // todo. should this status be an internal server error, or a bad request?
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }

        return Response.noContent().build();
    }
}
