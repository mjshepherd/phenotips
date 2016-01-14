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
package org.phenotips.data.permissions.rest;

import org.phenotips.data.rest.model.Collaborators;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * ToDo.
 *
 * @version $Id$
 * @since 1.2M5
 */
@Path("/patients/{patient-id}/permissions/visibility")
public interface CollaboratorResource
{
    /**
     * Todo. put a proper comment
     * The missing javadoc comment
     */
    @GET
    Collaborators getCollaborators(@PathParam("patient-id") String patientId);

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    Response putVisibilityWithJson(String json, @PathParam("patient-id") String patientId);

    @PUT
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    Response putVisibilityWithForm(@PathParam("patient-id") String patientId);
}
