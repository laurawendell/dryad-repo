/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.dryadwidgets;

import java.util.Map;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.selection.Selector;
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.utils.ContextUtil;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.identifier.DOIIdentifierProvider;
import org.dspace.identifier.IdentifierNotFoundException;
import org.dspace.identifier.IdentifierNotResolvableException;
import org.dspace.utils.DSpace;

/**
 * This simple selector operates on a DOI as 'identifier'.  It returns true if
 * the identifier is found, false if not.  Can be used to determine which banner
 * to render.
 *
 * The test expression is not used, but 'identifier' is a required parameter
 * 
 * @author Dan Leehr
 */

public class WidgetBannerSelector extends AbstractLogEnabled implements
        Selector
{

    private static Logger log = Logger.getLogger(WidgetBannerSelector.class);

    /**
     * Determine if the provided identifier is resolvable
     */
    public boolean select(String expression, Map objectModel,
            Parameters parameters) {
        try
        {
            Context context = ContextUtil.obtainContext(objectModel);
            String publisher = parameters.getParameter("publisher","unknown");
            String identifier = parameters.getParameter("identifier","unknown");

            // incoming identifier should be a DOI, try to resolve with DOIIdentifierPRovider
            DSpaceObject dso = null;
            DOIIdentifierProvider doiService = new DSpace().getSingletonService(DOIIdentifierProvider.class);
            try {
                dso = doiService.resolve(context, identifier,  new String[]{});
            } catch (IdentifierNotFoundException ex) {
                // ignoring the exception, leaving dso as null
            } catch (IdentifierNotResolvableException ex) {
                // ignoring the exception, leave dso as null
            }

            if (dso != null) {
                return true;
            } else {
                return false;
            }
        }
        catch (Exception e)
        {
            // Log it and returned no match.
            log.error("Error selecting based on provided identifier and publisher: "
                    + e.getMessage());

            return false;
        }
    }

}
