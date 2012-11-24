/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.activecq.tools.errorpagehandler.impl;

import com.activecq.tools.errorpagehandler.ErrorPageHandlerService;
import com.day.cq.commons.PathInfo;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import com.day.cq.wcm.api.WCMMode;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.engine.auth.Authenticator;
import org.apache.sling.engine.auth.NoAuthenticationHandlerException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(ErrorPageHandlerImpl.class);
    private static final String USER_AGENT = "User-Agent";
    private static final String MOZILLA = "Mozilla";
    private static final String OPERA = "Opera";

    /* Enable/Disable */
    private static final boolean DEFAULT_ENABLED = true;
    private boolean enabled = DEFAULT_ENABLED;
    @Property(label = "Enable",
    description = "Enables/Disables the error handler. [Required]",
    boolValue = DEFAULT_ENABLED)
    private static final String PROP_ENABLED = "prop.enabled";

    /* Minimum Search Depth */
    private static final int DEFAULT_MIN_SEARCH_DEPTH = 3;
    private int minSearchDepth = DEFAULT_MIN_SEARCH_DEPTH;
    @Property(label = "Minimum search depth",
    description = "The search for an appropriate error page will traverse UP the tree, stopping at each level and performing a search on every node equal to or greater than the 'current' depth. This setting prevents the lookup from performing too broad of a search. If sites are defined in the following structure '/content/site/en' the appropriate value would be: 3 [Optional] [Default: 3]",
    intValue = DEFAULT_MIN_SEARCH_DEPTH)
    private static final String PROP_MINS_SEARCH_DEPTH = "prop.search.depth-min";

    /* Error Page Extenstion */
    private static final String DEFAULT_ERROR_PAGE_EXTENSION = "html";
    private String errorPageExtension = DEFAULT_ERROR_PAGE_EXTENSION;
    @Property(label = "Error page extension",
    description = "Examples: html, htm, xml, json. [Optional] [Default: html]",
    value = DEFAULT_ERROR_PAGE_EXTENSION)
    private static final String PROP_ERROR_PAGE_EXTENSION = "prop.error-page.extension";

    /* Default Error Page Path */
    private static final String DEFAULT_ERROR_PAGE_PATH_DEFAULT = "";
    private String defaultErrorPagePath = DEFAULT_ERROR_PAGE_PATH_DEFAULT;
    @Property(label = "Default error page path",
    description = "Absolute path to Error page resource to serve if no error pages can be found. Does not include extension. [Optional... but highly recommended]",
    value = DEFAULT_ERROR_PAGE_PATH_DEFAULT)
    private static final String PROP_ERROR_PAGE_PATH = "prop.error-page.default-path";

    /* Search Paths */
    private static final String[] DEFAULT_SEARCH_PATHS = {"/content"};
    private String[] searchPaths = DEFAULT_SEARCH_PATHS;
    @Property(label = "Search paths",
    description = "The search will only look at candidate error pages (including Default error page name) if their paths start with the path prefixes here. Example: All error pages to live at or below a site locale folder 'errors': /content/site/en/errors. [Optional] [Default: /content]",
    cardinality = Integer.MAX_VALUE)
    private static final String PROP_SEARCH_PATHS = "prop.search.paths";

    /* Default Error Page Node Name */
    private static final String DEFAULT_ERROR_PAGE_NAME_DEFAULT = "error";
    private String defaultErrorPageName = DEFAULT_ERROR_PAGE_NAME_DEFAULT;
    @Property(label = "Default error page name",
    description = "Page name to look for if no specific error page (404, 500, Throwable, etc.) can be found. [Optional... but highly recommended] [Default: error]",
    value = DEFAULT_ERROR_PAGE_NAME_DEFAULT)
    private static final String PROP_ERROR_PAGE_NAME = "prop.error-page.default-node-name";

    @Reference
    private QueryBuilder queryBuilder;

    @Reference
    private Authenticator authenticator;

    private boolean hasSearchPaths;

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

        Resource page = null;
        // Get error page name to look for based on the error code/name
        final String pageName = getErrorPageName(request);
        log.debug("Error page name to find: {}", pageName);
        // Try to find the closest real parent for the requested resource
        final Resource parent = findFirstRealParent(errorResource);

        if (isValidParent(parent)) {
            // Search for CQ Page for specific servlet
            page = searchUp(parent, pageName);

            if(page == null) {
                // Search for CQ Page for default error page name
                log.debug("Could not find [{}] looking for default page: {}", pageName, getDefaultErrorPageName());
                page = searchUp(parent, getDefaultErrorPageName());
            }
        }


        if (page == null) {
            // If no error page could be found
            if (hasDefaultErrorPage()) {
                final String errorPage = applyExtenion(getDefaultErrorPagePath());
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

    public boolean hasDefaultErrorPage() {
        return StringUtils.isNotBlank(getDefaultErrorPagePath());
    }

    public boolean hasDefaultErrorPageName() {
        return StringUtils.isNotBlank(getDefaultErrorPageName());
    }

    @Override
    public String getDefaultErrorPagePath() {
        return defaultErrorPagePath;
    }

    @Override
    public String getDefaultErrorPageName() {
        return defaultErrorPageName;
    }

    @Override
    public String getErrorPageExtension() {
        return StringUtils.stripToEmpty(errorPageExtension);
    }

    private Resource searchUp(Resource searchResource, String pageName) {
        ResourceResolver resourceResolver = searchResource.getResourceResolver();
        Resource parent = searchResource.getParent();

        if (isValidParent(searchResource) && !isSearchPathParent(searchResource.getPath())) {
            return searchUp(parent, pageName);
        }

        Query query = makeQuery(searchResource, pageName);
        SearchResult result = query.getResult();
        List<Node> allNodes = IteratorUtils.toList(result.getNodes());
        List<Node> nodes = new ArrayList<Node>();

        // Filter results by the searchResource path; All valid results' paths should begin
        // with searchResource.getPath()
        for(Node node : allNodes) {
            if(node == null) { continue; }
            try {
                if(StringUtils.startsWith(node.getPath(), searchResource.getPath())) {
                    nodes.add(node);
                }
            } catch(RepositoryException ex) {
                // Keep looking; worst case all node access will fail and default error page will be used
                continue;
            }
        }

        if(nodes.isEmpty()) {
            if (isValidParent(parent)) {
                return searchUp(parent, pageName);
            }
        } else {
            for (Node node : nodes) {
                try {
                    if (isUnderSearchPath(node.getPath())) {
                        return resourceResolver.resolve(node.getPath());
                    }
                } catch (RepositoryException ex) {
                    // Keep looking; worst case all node access will fail and default error page will be used
                    continue;
                }
            }

            if (isValidParent(parent)) {
                return searchUp(parent, pageName);
            }
        }

        return null;
    }

    private Query makeQuery(Resource searchResource, String errorName) {
        ResourceResolver resourceResolver = searchResource.getResourceResolver();
        Session session = resourceResolver.adaptTo(Session.class);

        Map<String, String> map = new HashMap<String, String>();
        // Remove slow path filtering
        //map.put("path", searchResource.getPath());
        map.put("type", "cq:Page");
        map.put("nodename", escapeNodeName(errorName));

        return queryBuilder.createQuery(PredicateGroup.create(map), session);
    }

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

            Resource tmpResource = resourceResolver.getResource(tmpStr);

            if (tmpResource != null) {
                return tmpResource;
            }
        }

        return null;
    }

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

    private boolean isValidParent(Resource parent) {
        if (parent == null) {
            return false;
        }
        if (StringUtils.equals("/", parent.getPath())) {
            return false;
        }

        Node parentNode = parent.adaptTo(Node.class);
        if (parentNode == null) {
            return false;
        }

        int parentDepth = StringUtils.split(parent.getPath(), '/').length;

        if (parentDepth < minSearchDepth) {
            return false;
        }

        try {
            if (parentNode.getPrimaryNodeType().isNodeType("rep:root")) {
                return false;
            }
        } catch (Exception ex) {
            return false;
        }

        return true;
    }

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

    private boolean isUnderSearchPath(String path) {
        if (searchPaths == null) {
            return true;
        }
        if (searchPaths.length < 1) {
            return true;
        }

        for (String searchPath : searchPaths) {
            searchPath = StringUtils.stripToNull(searchPath);
            if (searchPath == null) {
                continue;
            }
            if (StringUtils.startsWith(path, searchPath)) {
                return true;
            }
        }

        return false;
    }

    private boolean isSearchPathParent(String path) {
        if (searchPaths == null) {
            return true;
        }
        if (searchPaths.length < 1) {
            return true;
        }

        for (String searchPath : searchPaths) {
            searchPath = StringUtils.stripToNull(searchPath);
            if (searchPath == null) {
                continue;
            }
            if (StringUtils.startsWith(searchPath, path)) {
                return true;
            }
        }

        return false;
    }

    protected boolean isAnonymousRequest(SlingHttpServletRequest request) {
        return (request.getAuthType() == null || request.getRemoteUser() == null);
    }

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


    protected void activate(ComponentContext componentContext) {
        configure(componentContext);
    }

    protected void deactivate(ComponentContext componentContext) {
        enabled = false;
    }

    private void configure(ComponentContext componentContext) {
        Dictionary properties = componentContext.getProperties();

        enabled = PropertiesUtil.toBoolean(properties.get(PROP_ENABLED), DEFAULT_ENABLED);

        defaultErrorPagePath = PropertiesUtil.toString(properties.get(PROP_ERROR_PAGE_PATH), DEFAULT_ERROR_PAGE_PATH_DEFAULT);
        defaultErrorPageName = PropertiesUtil.toString(properties.get(PROP_ERROR_PAGE_NAME), DEFAULT_ERROR_PAGE_NAME_DEFAULT);

        errorPageExtension = PropertiesUtil.toString(properties.get(PROP_ERROR_PAGE_EXTENSION), DEFAULT_ERROR_PAGE_EXTENSION);

        minSearchDepth = PropertiesUtil.toInteger(properties.get(PROP_MINS_SEARCH_DEPTH), DEFAULT_MIN_SEARCH_DEPTH);

        String[] configuredSearchPaths = PropertiesUtil.toStringArray(properties.get(PROP_SEARCH_PATHS), DEFAULT_SEARCH_PATHS);
        List<String> tmpSearchPaths = new ArrayList<String>();

        for (String sp : configuredSearchPaths) {
            String tmp = StringUtils.stripToNull(sp);

            if (tmp != null) {
                tmpSearchPaths.add(tmp);
            }
        }

        if (tmpSearchPaths.isEmpty()) {
            searchPaths = new String[0];
        } else {
            searchPaths = tmpSearchPaths.toArray(new String[0]);
        }

        /* Debug */
        log.debug("=======================================");

        log.debug("Enabled: " + isEnabled());
        log.debug("Default Error Page Path: " + getDefaultErrorPagePath());
        log.debug("Default Error Page Name: " + getDefaultErrorPageName());

        log.debug("Error Page Extension: " + getErrorPageExtension());
        log.debug("Minimum Search Depth: " + minSearchDepth);
        log.debug("Search Path Count: " + searchPaths.length);

        for(String searchPath : searchPaths) {
            log.debug("Search Path: " + searchPath);
        }

        log.debug("---------------------------------------");
    }
}
