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
import org.phenotips.data.permissions.Collaborator;
import org.phenotips.data.permissions.Owner;
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.permissions.rest.CollaboratorResource;
import org.phenotips.data.permissions.rest.DomainObjectFactory;
import org.phenotips.data.permissions.rest.Relations;
import org.phenotips.data.permissions.script.SecurePatientAccess;
import org.phenotips.data.rest.model.Collaborators;
import org.phenotips.data.rest.model.Link;
import org.phenotips.data.rest.model.PatientVisibility;
import org.phenotips.data.rest.model.PhenotipsUser;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.stability.Unstable;
import org.xwiki.users.User;
import org.xwiki.users.UserManager;

import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation of {@link DomainObjectFactory}.
 *
 * @version $Id$
 * @since 1.2M5
 */
@Unstable
@Component
@Singleton
public class DefaultDomainObjectFactory implements DomainObjectFactory, Initializable
{
    @Inject
    private AuthorizationManager access;

    @Inject
    private UserManager users;

    /** Provides access to the underlying data storage. */
    @Inject
    private DocumentAccessBridge documentAccessBridge;

    /** Parses string representations of document references into proper references. */
    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> stringResolver;

    /** Parses string representations of document references into proper references. */
    @Inject
    @Named("current")
    private DocumentReferenceResolver<EntityReference> referenceResolver;

    @Inject
    private PermissionsManager manager;

    @Inject
    private Logger logger;

    EntityReference userObjectReference;

    @Override
    public void initialize() throws InitializationException
    {
        this.userObjectReference = this.stringResolver.resolve("XWiki.XWikiUsers");
    }

    @Override
    public PhenotipsUser createPatientOwner(Patient patient)
    {
        // todo. common security checks
        if (patient == null) {
            return null;
        }
        User currentUser = this.users.getCurrentUser();
        if (!this.access.hasAccess(Right.VIEW, currentUser == null ? null : currentUser.getProfileDocument(),
            patient.getDocument())) {
            return null;
        }

        // todo. is this allowed?
        PatientAccess patientAccess = new SecurePatientAccess(this.manager.getPatientAccess(patient), this.manager);
        Owner owner = patientAccess.getOwner();

        // links should be added at a later point, to allow the reuse of this method in different contexts

        return createPhenotipsUser(owner.getUsername(), owner.getType(), owner.getUser());
    }

    private PhenotipsUser createPhenotipsUser(String username, String type, EntityReference reference)
    {
        PhenotipsUser result = new PhenotipsUser();

        result.withId(username);
        result.withType(type);

        // there is a chance of not being able to retrieve the rest of the data,
        // which should not prevent the returning of `id` and `type`
        try {
            DocumentReference userRef = this.referenceResolver.resolve(reference);
            XWikiDocument userDoc = (XWikiDocument) this.documentAccessBridge.getDocument(userRef);
            BaseObject userObj = userDoc.getXObject(this.userObjectReference);

            String email = userObj.getStringValue("email");
            StringBuilder nameBuilder = new StringBuilder();
            nameBuilder.append(userObj.getStringValue("first_name"));
            nameBuilder.append(" ");
            nameBuilder.append(userObj.getStringValue("last_name"));
            String name = nameBuilder.toString().trim();

            result.withName(name);
            result.withEmail(email);
        } catch (Exception ex) {
            this.logger.error("Could not load user's or group's document", ex.getMessage());
        }
        return result;
    }

    public PatientVisibility createPatientVisibility(Patient patient)
    {
        // todo. common security checks
        if (patient == null) {
            return null;
        }
        User currentUser = this.users.getCurrentUser();
        if (!this.access.hasAccess(Right.VIEW, currentUser == null ? null : currentUser.getProfileDocument(),
            patient.getDocument())) {
            return null;
        }

        PatientVisibility result = new PatientVisibility();
        // todo. is this allowed?
        PatientAccess patientAccess = new SecurePatientAccess(this.manager.getPatientAccess(patient), this.manager);
        Visibility visibility = patientAccess.getVisibility();

        result.withLevel(visibility.getName());

        return result;
    }

    public Collaborators createCollaborators(Patient patient, UriInfo uriInfo)
    {
        if (!commonSecurityChecks(patient)) {
            return null;
        }

        PatientAccess patientAccess = new SecurePatientAccess(this.manager.getPatientAccess(patient), this.manager);
        Collection<Collaborator> collaborators = patientAccess.getCollaborators();

        Collaborators result = new Collaborators();
        for (Collaborator collaborator : collaborators)
        {
            result.withCollaborators(this.createCollaborator(collaborator, uriInfo));
        }

        return result;
    }

    private PhenotipsUser createCollaborator(Collaborator collaborator, UriInfo uriInfo)
    {
        PhenotipsUser result =
            this.createPhenotipsUser(collaborator.getUsername(), collaborator.getType(), collaborator.getUser());

        String href = uriInfo.getBaseUriBuilder().path(CollaboratorResource.class)
            .build(collaborator.getUser().getName()).toString();
        result.withLinks(new Link().withRel(Relations.COLLABORATOR).withHref(href));

        return result;
    }

    private boolean commonSecurityChecks(Patient patient)
    {
        if (patient == null) {
            return false;
        }
        User currentUser = this.users.getCurrentUser();
        if (!this.access.hasAccess(Right.VIEW, currentUser == null ? null : currentUser.getProfileDocument(),
            patient.getDocument())) {
            return false;
        }
        return true;
    }
}
