/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.discovery;

import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.SearchUtils;
import org.dspace.discovery.SolrServiceImpl;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preform a simple search of the repository. The user provides a simple one
 * field query (the url parameter is named query) and the results are processed.
 * 
 * @author Kevin Van de Velde (kevin at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 *
 * Class has been adjusted to support the non lowercased facets
 */
public class SimpleSearch extends AbstractSearch implements
		CacheableProcessingComponent {

    private static final Logger log = Logger.getLogger(SimpleSearch.class);

	/**
	 * Language Strings
	 */
	private static final Message T_title = message("xmlui.ArtifactBrowser.SimpleSearch.title");

	private static final Message T_dspace_home = message("xmlui.general.dspace_home");

	private static final Message T_trail = message("xmlui.ArtifactBrowser.SimpleSearch.trail");

	private static final Message T_head = message("xmlui.ArtifactBrowser.SimpleSearch.head");

	private static final Message T_full_text_search = message("xmlui.ArtifactBrowser.SimpleSearch.full_text_search");

	private static final Message T_go = message("xmlui.general.go");
	private static final Message T_FILTER_HELP = message("xmlui.Discovery.SimpleSearch.filter_help");
	private static final Message T_FILTER_HEAD = message("xmlui.Discovery.SimpleSearch.filter_head");
	private static final Message T_FILTERS_SELECTED = message("xmlui.ArtifactBrowser.SimpleSearch.filter.selected");
        private static final Message T_FILTER_TEXT_LABEL = message("xmlui.Discovery.SimpleSearch.filter_text");

	/**
	 * Add Page metadata.
	 */
	public void addPageMeta(PageMeta pageMeta) throws WingException,
			SQLException, AuthorizeException {
		pageMeta.addMetadata("title").addContent(T_title);
		pageMeta.addTrailLink(contextPath + "/", T_dspace_home);

		DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
		if ((dso instanceof Collection) || (dso instanceof Community)) {
			HandleUtil.buildHandleTrail(dso, pageMeta, contextPath);
		}

		pageMeta.addTrail().addContent(T_trail);
	}

	/**
	 * build the DRI page representing the body of the search query. This
	 * provides a widget to generate a new query and list of search results if
	 * present.
	 */
	public void addBody(Body body) throws SAXException, WingException,
			UIException, SQLException, IOException, AuthorizeException {

		String queryString = getQuery();

		// Build the DRI Body
		Division search = body.addDivision("search", "primary");
		search.setHead(getHead());

		String searchUrl = ConfigurationManager.getProperty("dspace.url")
				+ "/JSON/discovery/searchSolr";

		search.addHidden("solr-search-url").setValue(searchUrl);
		search.addHidden("contextpath").setValue(contextPath);

		String[] fqs = getParameterFilterQueries();

		Division query = search.addInteractiveDivision("general-query",
				getDiscoverUrl(), Division.METHOD_GET, "secondary search");

		List queryList = query.addList("search-query", List.TYPE_FORM);

		Text text = queryList.addItem().addText("query");
		text.setLabel(T_full_text_search);
		text.setValue(queryString);

		// If we have any filters, show them
		if (fqs.length > 0) {
			Composite composite = queryList.addItem().addComposite(
					"facet-controls");


            boolean addOptions=false;
			CheckBox box = composite.addCheckBox("fq");

			for (String name : fqs) {
				String field = name;
				String value = name;
                if(!field.contains("location"))
                {
                    addOptions=true;
                }
				// We treat our locations differently; want them to work behind
				// the scenes
				if (name.startsWith("location:")) {
					query.addHidden("fq").setValue(name);
					continue;
				}
                if (name.startsWith("location.coll:")) {
                    query.addHidden("fq").setValue(name);
                    continue;
                }
				if (name.contains(":")) {
					field = name.split(":")[0];
					value = name.split(":")[1];
				}
				else {
					// We have got no field, so we are using everything
					field = "*";
				}

				value = value.replace("\\", "");
				if ("*".equals(field)) {
					field = "all";
				}
				if (name.startsWith("*:")) {
					name = name.substring(name.indexOf(":") + 1, name.length());
				}

				Option option = box.addOption(true, name);
				option.addContent(message("xmlui.ArtifactBrowser.SimpleSearch.filter."
						+ field));

				if (field.equals("location.comm")
						|| field.equals("location.coll")) {
					// We have a community/collection, resolve it to a
					// dspaceObject
					value = SolrServiceImpl.locationToName(context, field,
							value);
				}

				String splitChar = SearchUtils.getConfig().getString(
						"solr.facets.split.char");
				if (field.endsWith("_filter") && splitChar != null
						&& value.indexOf(splitChar) != -1)
					value = value.substring(value.indexOf(splitChar)
							+ splitChar.length(), value.length());
				// Check for a range query
				Pattern pattern = Pattern.compile("\\[(.*? TO .*?)\\]");
				Matcher matcher = pattern.matcher(value);
				boolean hasPattern = matcher.find();
				if (hasPattern) {
					String[] years = matcher.group(0).replace("[", "")
							.replace("]", "").split(" TO ");
					option.addContent(": " + years[0] + " - " + years[1]);
					continue;
				}

				option.addContent(": " + value);

			}
            if(addOptions)
            {
                composite.setLabel(T_FILTERS_SELECTED);
            }
		}

		java.util.List<String> filterFields = SearchUtils.getSearchFilters();
		if (0 < filterFields.size()) {
			// We have at least one filter so add our filter box
			Item item = queryList.addItem("search-filter-list",
					"search-filter-list");
			Composite filterComp = item.addComposite("search-filter-controls");
			filterComp.setHelp(T_FILTER_HELP);

			Select select = filterComp.addSelect("filtertype", "label-at-left-nobr");
			// First of all add a default filter
			select.addOption("*",
					message("xmlui.ArtifactBrowser.SimpleSearch.filter.all"));
			// For each field found (at least one) add options
                        select.setLabel(T_FILTER_HEAD);

			for (String field : filterFields) {
				select.addOption(field,
						message("xmlui.ArtifactBrowser.SimpleSearch.filter."
								+ field));
			}

			// Add a box so we can search for our value
                        // This needs to set the label
			Text filterField = filterComp.addText("filter", "label-at-left-nobr");
                        filterField.setLabel(T_FILTER_TEXT_LABEL);

			// And last add an add button
			filterComp.enableAddOperation();
		}

		buildSearchControls(query);

		// Add the result division
		try {
			buildSearchResultsDivision(search);
		}
		catch (SearchServiceException e) {
			throw new UIException(e.getMessage(), e);
		}

	}

    public Message getHead() {
        return T_head;
    }

	/**
	 * Returns a list of the filter queries for use in rendering pages, creating
	 * page more urls, ....
	 * 
	 * @return an array containing the filter queries
	 */
	protected String[] getParameterFilterQueries() {
		Request request = ObjectModelHelper.getRequest(objectModel);
		java.util.List<String> fqs = new ArrayList<String>();
		if (request.getParameterValues("fq") != null) {
			fqs.addAll(Arrays.asList(request.getParameterValues("fq")));
		}

		// Have we added a filter using the UI
		if (request.getParameter("filter") != null
				&& !"".equals(request.getParameter("filter"))
				&& request.getParameter("submit_search-filter-controls_add") != null) {
			fqs.add((request.getParameter("filtertype").equals("*") ? ""
					: request.getParameter("filtertype") + ":")
					+ request.getParameter("filter"));
		}
		return fqs.toArray(new String[fqs.size()]);
	}

	/**
	 * Returns all the filter queries for use by solr This method returns more
	 * expanded filter queries then the getParameterFilterQueries
	 * 
	 * @return an array containing the filter queries
	 */
	protected String[] getSolrFilterQueries() {
		try {
			java.util.List<String> allFilterQueries = new ArrayList<String>();
			Request request = ObjectModelHelper.getRequest(objectModel);
			java.util.List<String> fqs = new ArrayList<String>();

			if (request.getParameterValues("fq") != null) {
				fqs.addAll(Arrays.asList(request.getParameterValues("fq")));
			}

			String type = request.getParameter("filtertype");
			String value = request.getParameter("filter");

			if (value != null
					&& !value.equals("")
					&& request
							.getParameter("submit_search-filter-controls_add") != null) {
				String exactFq = (type.equals("*") ? "" : type + ":") + value;
                if(!exactFq.contains("location.coll")){
				fqs.add(exactFq + " OR " + exactFq + "*");
			}
            }

			for (String fq : fqs) {
				// Do not put a wildcard after a range query
                if (fq.matches(".*\\:\\[.* TO .*\\](?![a-z 0-9]).*") || fq.contains("location.coll")) {
					allFilterQueries.add(fq);
				}
				else {

					allFilterQueries.add(fq.endsWith("*") ? fq : fq + " OR "
							+ fq + "*");
				}
			}

			return allFilterQueries
					.toArray(new String[allFilterQueries.size()]);
		}
		catch (RuntimeException re) {
			throw re;
		}
		catch (Exception e) {
            log.error("Error while retrieving solr filter queries", e);
			return null;
		}
	}

	/**
	 * Get the search query from the URL parameter, if none is found the empty
	 * string is returned.
	 */
	protected String getQuery() throws UIException {
		Request request = ObjectModelHelper.getRequest(objectModel);
		String query = decodeFromURL(request.getParameter("query"));
		if (query == null) {
			return "";
		}
		return query.trim();
	}

	/**
	 * Generate a url to the simple search url.
	 */
	protected String generateURL(Map<String, String> parameters)
			throws UIException {
		String query = getQuery();
		if (!"".equals(query)) {
			parameters.put("query", encodeForURL(query));
		}

		if (parameters.get("page") == null) {
			parameters.put("page", String.valueOf(getParameterPage()));
		}

		if (parameters.get("rpp") == null) {
			parameters.put("rpp", String.valueOf(getParameterRpp()));
		}

		if (parameters.get("group_by") == null) {
			parameters
					.put("group_by", String.valueOf(this.getParameterGroup()));
		}

		if (parameters.get("sort_by") == null) {
			parameters.put("sort_by", String.valueOf(getParameterSortBy()));
		}

		if (parameters.get("order") == null) {
			parameters.put("order", getParameterOrder());
		}

		if (parameters.get("etal") == null) {
			parameters.put("etal", String.valueOf(getParameterEtAl()));
		}

		return super.generateURL(getDiscoverUrl(), parameters);
	}

	public String getView() {
		return "search";
	}

	/**
	 * Encode the given string for URL transmission.
	 * 
	 * @param unencodedString The unencoded string.
	 * @return The encoded string
	 */
	public static String encodeForURL(String unencodedString)
			throws UIException {
		if (unencodedString == null) {
			return "";
		}

		try {
			return URLEncoder.encode(unencodedString,
					Constants.DEFAULT_ENCODING);
		}
		catch (UnsupportedEncodingException uee) {
			throw new UIException(uee);
		}

	}

	/**
	 * Decode the given string from URL transmission.
	 * 
	 * @param encodedString The encoded string.
	 * @return The unencoded string
	 */
	public static String decodeFromURL(String encodedString) throws UIException {
		if (encodedString == null) {
			return null;
		}

		try {
			// Percent(%) is a special character, and must first be escaped as
			// %25
			if (encodedString.contains("%")) {
				encodedString = encodedString.replace("%", "%25");
			}

			return URLDecoder.decode(encodedString, Constants.DEFAULT_ENCODING);
		}
		catch (UnsupportedEncodingException uee) {
			throw new UIException(uee);
		}

	}

}
