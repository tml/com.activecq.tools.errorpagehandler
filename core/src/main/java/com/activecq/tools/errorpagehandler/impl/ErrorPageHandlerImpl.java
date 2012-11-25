/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.activecq.tools.errorpagehandler.impl;

import com.activecq.api.utils.OsgiPropertyUtil;
import com.activecq.tools.errorpagehandler.ErrorPageHandlerService;
import com.day.cq.commons.PathInfo;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import com.day.cq.wcm.api.WCMMode;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.engine.auth.Authenticator;
import org.apache.sling.engine.auth.NoAuthenticationHandlerException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author david
 */
@Component(label = "ActiveCQ - Error Page Handler",
description = "Handles the resolution of the proper Error pages to display.",
immediate = false,
metatype = true)
@Properties({
    @Property(
        name = "service.vendor",
    value = "ActiveCQ")
})
@Service
public class ErrorPageHandlerImpl implements ErrorPageHandlerService {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ErrorPageHandlerImpl.class);

    private static final String USER_AGENT = "User-Agent";
    private static final String MOZILLA = "Mozilla";
    private static final String OPERA = "Opera";
    public static final String DEFAULT_ERROR_PAGE_NAME = "errors";

    /* Enable/Disable */
    private static final boolean DEFAULT_ENABLED = true;
    private boolean enabled = DEFAULT_ENABLED;
    @Property(label = "Enable",
    description = "Enables/Disables the error handler. [Required]",
    boolValue = DEFAULT_ENABLED)
    private static final String PROP_ENABLED = "prop.enabled";

    /* Error Page Extenstion */
    private static final String DEFAULT_ERROR_PAGE_EXTENSION = "html";
    private String errorPageExtension = DEFAULT_ERROR_PAGE_EXTENSION;
    @Property(label = "Error page extension",
    description = "Examples: html, htm, xml, json. [Optional] [Default: html]",
    value = DEFAULT_ERROR_PAGE_EXTENSION)
    private static final String PROP_ERROR_PAGE_EXTENSION = "prop.error-page.extension";

    /* Default Error Page Path */
    private static final String DEFAULT_SYSTEM_ERROR_PAGE_PATH_DEFAULT = "";
    private String systemErrorPagePath = DEFAULT_SYSTEM_ERROR_PAGE_PATH_DEFAULT;
    @Property(label = "Default system error page path",
    description = "Absolute path to system Error page resource to serve if no other more appropriate error pages can be found. Does not include extension. [Optional... but highly recommended]",
    value = DEFAULT_SYSTEM_ERROR_PAGE_PATH_DEFAULT)
    private static final String PROP_ERROR_PAGE_PATH = "prop.error-page.system-path";

    /* Search Paths */
    private static final String[] DEFAULT_SEARCH_PATHS = {};
    private String[] searchPaths = DEFAULT_SEARCH_PATHS;
    @Property(label = "Search paths",
    description = "List of valid inclusive content trees under which error pages may reside, along with the name of the the default error page for the content tree. Example: /content/geometrixx/en:errors [Optional]",
    cardinality = Integer.MAX_VALUE)
    private static final String PROP_SEARCH_PATHS = "prop.search.paths";

    @Reference
    private QueryBuilder queryBuilder;

    @Reference
    private Authenticator authenticator;

    private SortedMap<String, String> searchPathMap = new TreeMap<String, String>();

    /**
     * Find the full path to the most appropriate Error Page
     *
     * @param request
     * @param errorResource
     * @return
     */
    @Override
    public String findErrorPage(SlingHttpServletRequest request, Resource errorResource) {
        if (!isEnabled()) {
            return null;
        }

        final ResourceResolver resourceResolver = errorResource.getResourceResolver();
        Resource page = null;
        // Get error page name to look for based on the error code/name
        final String pageName = getErrorPageName(request);

        // Try to find the closest real parent for the requested resource
        final Resource parent = findFirstRealParent(errorResource);

        if (!this.searchPathMap.isEmpty()) {
            final String searchPath = this.getSearchPath(parent.getPath());
            // Search for CQ Page for specific servlet named Page (404, 500, Throwable, etc.)
            SearchResult result = executeQuery(resourceResolver, pageName);
            List<Node> nodes = validateNodes(searchPath, IteratorUtils.toList(result.getNodes()));

            for (Node node : nodes) {
                try {
                    // Finds the longest search Path this matching page resides under; Null if no searchPaths are matched
                    log.debug("Node: {}", node.getPath());
                    page = validateResource(resourceResolver, node);
                    if(page != null) { break; }
                } catch (RepositoryException ex) {
                    // Keep looking; worst case all node access will fail and default error page will be used
                    continue;
                }
            }

            if(page == null) {
                if(StringUtils.isNotBlank(searchPath)) {
                    page = resourceResolver.resolve(searchPath);
                }
            }
        }

        if (page == null || ResourceUtil.isNonExistingResource(page)) {
            // If no error page could be found
            if (hasSystemErrorPage()) {
                final String errorPage = applyExtenion(getSystemErrorPagePath());
                log.debug("Using default error page: {}", errorPage);
                return StringUtils.stripToNull(errorPage);
            }
        } else {
            final String errorPage = applyExtenion(page.getPath());
            log.debug("Using resolved error page: {}", errorPage);
            return StringUtils.stripToNull(errorPage);
        }

        return null;
    }


    /**
     * Create the query for finding candidate cq:Pages
     *
     * @param searchResource
     * @param pageNames
     * @return
     */
    private SearchResult executeQuery(ResourceResolver resourceResolver, String... pageNames) {
        Session session = resourceResolver.adaptTo(Session.class);

        Map<String, String> map = new HashMap<String, String>();
        // Remove slow path filtering
        map.put("type", "cq:Page");

        if(pageNames.length == 1) {
            map.put("nodename", escapeNodeName(pageNames[0]));
        } else if(pageNames.length > 1) {
            map.put("group.p.or", "true");
            for(int i = 0; i < pageNames.length; i++) {
                map.put("group."+String.valueOf(i) + "_nodename", escapeNodeName(pageNames[i]));
            }
        }

        final Query query = queryBuilder.createQuery(PredicateGroup.create(map), session);
        return query.getResult();
    }


    /**
     * Double check that the candidate CQ Page resource is valid
     *
     * @param resourceResolver
     * @param node
     * @param searchPath
     * @return
     * @throws RepositoryException
     */
    private Resource validateResource(ResourceResolver resourceResolver, Node node) throws RepositoryException {
        // Double check that the resource exists and return it as a match
        final Resource resource = resourceResolver.getResource(node.getPath());

        if(resource != null && !ResourceUtil.isNonExistingResource(resource)) {
            return resource;
        }

        return null;
    }

    /**
     * Filter the query results based on the paths
     *
     * @param searchResource
     * @param allNodes
     * @return
     */
    private List<Node> validateNodes(String searchPath, List<Node> allNodes) {
        List<Node> nodes = new ArrayList<Node>();

        // Filter results by the searchResource path; All valid results' paths should begin
        // with searchResource.getPath()
        for(Node node : allNodes) {
            if(node == null) { continue; }
            try {
                // Make sure all query results under or equals to the current Search Resource
                if(StringUtils.equals(node.getPath(), searchPath) ||
                    StringUtils.startsWith(node.getPath(), searchPath.concat("/"))) {
                    nodes.add(node);
                }
            } catch(RepositoryException ex) {
                // Keep looking; worst case all node access will fail and default error page will be used
                continue;
            }
        }

        return nodes;
    }









    /**
     * Get Error Status Code from Request
     *
     * @param request
     * @return
     */
    @Override
    public int getStatusCode(SlingHttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute(ErrorPageHandlerService.STATUS_CODE);

        if (statusCode != null) {
            return statusCode.intValue();
        } else {
            return ErrorPageHandlerService.DEFAULT_STATUS_CODE;
        }
    }

    @Override
    public String getErrorPageName(SlingHttpServletRequest request) {
        String statusCode = String.valueOf(getStatusCode(request));
        String servletName = statusCode;
        String servletPath = (String) request.getAttribute(ErrorPageHandlerService.SERVLET_NAME);

        PathInfo pathInfo = new PathInfo(servletPath);
        String[] parts = StringUtils.split(pathInfo.getResourcePath(), '/');
        if (parts.length > 0) {
            servletName = parts[parts.length - 1];
        }

        return StringUtils.lowerCase(servletName);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasSystemErrorPage() {
        return StringUtils.isNotBlank(getSystemErrorPagePath());
    }

    public boolean hasDefaultErrorPagePath(String path) {
        return StringUtils.isNotBlank(getDefaultErrorPagePath(path));
    }

    @Override
    public String getSystemErrorPagePath() {
        return systemErrorPagePath;
    }

    @Override
    public String getDefaultErrorPagePath(String path) {
        if(this.searchPathMap.containsKey(path)) {
            return (String) this.searchPathMap.get(path);
        } else {
            return null;
        }
    }

    /**
     * Get the sorted Search Paths
     *
     * @return
     */
    private List<String> getSearchPaths() {
        return Arrays.asList(this.searchPathMap.keySet().toArray(new String[]{}));
    }

    /**
     *
     * @param path
     * @return
     */
    private String getSearchPath(String path) {
        for(String searchPath : this.getSearchPaths()) {
            if(StringUtils.equals(path, searchPath) ||
                    StringUtils.startsWith(path, searchPath.concat("/"))) {
                return getDefaultErrorPagePath(searchPath);
            }
        }
        return null;
    }

    @Override
    public String getErrorPageExtension() {
        return StringUtils.stripToEmpty(errorPageExtension);
    }




    /**
     * Given the Request path, find the first Real Parent of the Request (even if the resource doesnt exist)
     *
     * @param resource
     * @return
     */
    private Resource findFirstRealParent(Resource resource) {
        ResourceResolver resourceResolver = resource.getResourceResolver();

        if (resource.getParent() != null) {
            return resource.getParent();
        }

        String path = resource.getPath();
        PathInfo pathInfo = new PathInfo(path);
        String[] parts = StringUtils.split(pathInfo.getResourcePath(), '/');

        for (int i = parts.length - 1; i >= 0; i--) {
            String[] tmpArray = (String[]) ArrayUtils.subarray(parts, 0, i);
            String tmpStr = "/".concat(StringUtils.join(tmpArray, '/'));

            final Resource tmpResource = resourceResolver.getResource(tmpStr);

            if (tmpResource != null) {
                return tmpResource;
            }
        }

        return null;
    }

    /**
     * Add extension as configured via OSGi Component Property
     *
     * Defaults to .html
     *
     * @param path
     * @return
     */
    private String applyExtenion(String path) {
        if (path == null) {
            return path;
        }

        String ext = getErrorPageExtension();
        if (StringUtils.isBlank(ext)) {
            return path;
        }

        return StringUtils.stripToEmpty(path).concat(".").concat(ext);
    }


    /**
     * Escapes JCR node names for search; Especially important for nodes that start with numbers
     *
     * @param name
     * @return
     */
    private String escapeNodeName(String name) {
        name = StringUtils.stripToNull(name);
        if (name == null) {
            return "";
        }

        char c = name.charAt(0);

        try {
            Integer.parseInt(String.valueOf(c));
            if (name.length() >= 2) {
                return "_x003" + c + "_" + StringUtils.substring(name, 1);
            } else {
                return "_x003" + c + "_";
            }
        } catch (NumberFormatException ex) {
            // DO NOTHING; not an int
        }

        return name;
    }


    /**
     *
     * @param request
     * @return
     */
    protected boolean isAnonymousRequest(SlingHttpServletRequest request) {
        return (request.getAuthType() == null || request.getRemoteUser() == null);
    }

    /**
     *
     * @param request
     * @return
     */
    protected boolean isBrowserRequest(SlingHttpServletRequest request) {
        final String userAgent = request.getHeader(USER_AGENT);
        if (StringUtils.isBlank(userAgent)) {
            return false;
        }
        return (StringUtils.contains(userAgent, MOZILLA) || StringUtils.contains(userAgent, OPERA));
    }

    /**
     * Determines if the Request is to Author
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAuthorModeRequest(SlingHttpServletRequest request) {
        final WCMMode mode = WCMMode.fromRequest(request);
        return (mode != WCMMode.DISABLED);
    }

    /**
     * Determines is the Request is to Author in Preview mode
     *
     * @param request
     * @return true if Request is to an Author in Preview
     */
    @Override
    public boolean isAuthorPreviewModeRequest(SlingHttpServletRequest request) {
        final WCMMode mode = WCMMode.fromRequest(request);
        return (mode == WCMMode.PREVIEW);
    }

    protected void authenticateRequest(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        if (authenticator == null) {
            log.warn("Cannot login: Missing Authenticator service");
            return;
        }

        try {
            authenticator.login(request, response);
        } catch (NoAuthenticationHandlerException ex) {
            log.warn("Cannot login: No Authentication Handler is willing to authenticate");
        }
    }

    /**
     * Determine and handle 404 Requests
     *
     * @param request
     * @param response
     */
    @Override
    public void doHandle404(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        if(!isAuthorModeRequest(request)) {
            return;
        } else if (getStatusCode(request) != SlingHttpServletResponse.SC_NOT_FOUND) {
            return;
        }

        if (isAnonymousRequest(request) && isBrowserRequest(request)) {
            authenticateRequest(request, response);
        }
    }

    /**
     * Returns the Exception Message from the Request
     *
     * @param request
     * @return
     */
    @Override
    public String getException(SlingHttpServletRequest request) {
        StringWriter stringWriter = new StringWriter();
        if (request.getAttribute(SlingConstants.ERROR_EXCEPTION) instanceof Throwable) {
            Throwable throwable = (Throwable) request.getAttribute(SlingConstants.ERROR_EXCEPTION);

            if (throwable == null) {
                return "";
            }

            if (throwable instanceof ServletException) {
                ServletException se = (ServletException) throwable;
                while (se.getRootCause() != null) {
                    throwable = se.getRootCause();
                    if (throwable instanceof ServletException) {
                        se = (ServletException) throwable;
                    } else {
                        break;
                    }
                }
            }

            throwable.printStackTrace(new PrintWriter(stringWriter, true));
        }

        return stringWriter.toString();
    }

    /**
     *
     * @param request
     * @return
     */
    @Override
    public String getRequestProgress(SlingHttpServletRequest request) {
        StringWriter stringWriter = new StringWriter();
        if (request != null) {
            RequestProgressTracker tracker = request.getRequestProgressTracker();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            tracker.dump(new PrintWriter(stringWriter, true));
        }
        return stringWriter.toString();
    }



    /**
     * Covert OSGi Property into a SortMap
     *
     * @param paths
     * @return
     */
    private SortedMap<String, String> searchPathsToMap(String[] paths) {
        SortedMap<String, String> sortedMap = new TreeMap<String, String>(new StringLengthComparator());

        for (String path : paths) {
            SimpleEntry tmp = OsgiPropertyUtil.toSimpleEntry(path, ":");

            String key = StringUtils.strip((String) tmp.getKey());
            String val = StringUtils.strip((String) tmp.getValue());

            // Only accept absolute paths
            if(StringUtils.isBlank(key) || !StringUtils.startsWith(key, "/")) { continue; }

            // Validate page name value
            if(StringUtils.isBlank(val)) {
                val = key + "/" + DEFAULT_ERROR_PAGE_NAME;
            } else if(StringUtils.equals(val, ".")) {
                val = key;
            } else if(!StringUtils.startsWith(val, "/")) {
                val = key + "/" + val;
            }

            sortedMap.put(key, val);
        }

        return sortedMap;
    }

    /** OSGi Component Methods **/

    protected void activate(ComponentContext componentContext) {
        configure(componentContext);
    }

    protected void deactivate(ComponentContext componentContext) {
        enabled = false;
    }

    private void configure(ComponentContext componentContext) {
        Dictionary properties = componentContext.getProperties();

        this.enabled = PropertiesUtil.toBoolean(properties.get(PROP_ENABLED), DEFAULT_ENABLED);

        this.systemErrorPagePath = PropertiesUtil.toString(properties.get(PROP_ERROR_PAGE_PATH), DEFAULT_SYSTEM_ERROR_PAGE_PATH_DEFAULT);

        this.errorPageExtension = PropertiesUtil.toString(properties.get(PROP_ERROR_PAGE_EXTENSION), DEFAULT_ERROR_PAGE_EXTENSION);

        this.searchPathMap = searchPathsToMap(PropertiesUtil.toStringArray(properties.get(PROP_SEARCH_PATHS), DEFAULT_SEARCH_PATHS));

        /* Debug */
        log.debug("=== ActiveCQ Tools - Error Page Handler ===");

        log.debug("Enabled: " + isEnabled());
        log.debug("Default Error Page Path: " + getSystemErrorPagePath());

        log.debug("Error Page Extension: " + getErrorPageExtension());

        log.debug("Search Path Map: " + searchPathMap.toString());

        log.debug("-------------------------------------------");
    }
}